package io.github.chan808.authtemplate.auth.repository

import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import org.springframework.data.redis.core.StringRedisTemplate
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
}
