package io.github.chan808.authtemplate.auth.repository

import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

@Component
class RefreshTokenStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val RT_PREFIX = "RT:"
        private const val LOCK_PREFIX = "LOCK:REISSUE:"
        private const val LOCK_TTL = 3L
        private const val MEMBER_SESSIONS_PREFIX = "MEMBER_SESSIONS:"
        // 세트 TTL = 절대 세션 최대치(30일): 어떤 세션도 이 기간 이후엔 존재하지 않음
        private const val MEMBER_SESSIONS_TTL = 30L * 24 * 3600

        // SMEMBERS → DEL을 단일 Lua 트랜잭션으로 처리
        // 이유: 두 명령 사이에 새 sid가 SADD되면 그 세션은 삭제되지 않고 잔류할 수 있음
        // Lua 스크립트는 Redis 싱글스레드에서 원자적으로 실행되므로 이 gap을 제거함
        private val DELETE_ALL_SESSIONS_SCRIPT = DefaultRedisScript(
            """
            local setKey = KEYS[1]
            local rtPrefix = ARGV[1]
            local sids = redis.call('SMEMBERS', setKey)
            for _, sid in ipairs(sids) do
                redis.call('DEL', rtPrefix .. sid)
            end
            redis.call('DEL', setKey)
            return #sids
            """.trimIndent(),
            Long::class.java,
        )
    }

    fun save(sid: String, session: RefreshTokenSession, ttlSeconds: Long) {
        redisTemplate.opsForValue().set(
            "$RT_PREFIX$sid",
            objectMapper.writeValueAsString(session),
            ttlSeconds,
            TimeUnit.SECONDS,
        )
    }

    fun find(sid: String): RefreshTokenSession? =
        redisTemplate.opsForValue().get("$RT_PREFIX$sid")
            ?.let { objectMapper.readValue(it, RefreshTokenSession::class.java) }

    fun delete(sid: String) {
        redisTemplate.delete("$RT_PREFIX$sid")
    }

    // SETNX: 락 획득 성공 시 true, 이미 존재하면 false → 재발급 중복 요청 차단
    fun tryLock(sid: String): Boolean =
        redisTemplate.opsForValue().setIfAbsent("$LOCK_PREFIX$sid", "1", LOCK_TTL, TimeUnit.SECONDS) ?: false

    fun releaseLock(sid: String) {
        redisTemplate.delete("$LOCK_PREFIX$sid")
    }

    // 로그인 시 sid를 회원별 세션 세트에 등록 → 비밀번호 변경/재설정 시 전체 세션 일괄 무효화에 활용
    fun addSession(memberId: Long, sid: String) {
        val key = "$MEMBER_SESSIONS_PREFIX$memberId"
        redisTemplate.opsForSet().add(key, sid)
        // TTL 갱신: 신규 세션 추가 시마다 절대 최대치로 연장해 세트가 조기 만료되지 않도록 보장
        redisTemplate.expire(key, MEMBER_SESSIONS_TTL, TimeUnit.SECONDS)
    }

    fun deleteAllSessionsForMember(memberId: Long) {
        val setKey = "$MEMBER_SESSIONS_PREFIX$memberId"
        redisTemplate.execute(DELETE_ALL_SESSIONS_SCRIPT, listOf(setKey), RT_PREFIX)
    }
}
