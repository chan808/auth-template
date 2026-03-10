package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.RateLimitException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class SignupRateLimitService(private val redisTemplate: StringRedisTemplate) {

    private val log = LoggerFactory.getLogger(SignupRateLimitService::class.java)

    // INCR + EXPIRE 원자적 처리: INCR 후 서버 장애 시 TTL 미설정으로 key가 영구 잔류하는 엣지케이스 방지
    private val script = DefaultRedisScript(
        """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
        end
        return count
        """.trimIndent(),
        Long::class.java,
    )

    fun check(ip: String) {
        val key = "RATE:SIGNUP:IP:$ip"
        val ttlSeconds = 3600L
        val limit = 5

        val count = redisTemplate.execute(script, listOf(key), ttlSeconds.toString()) ?: 0L
        if (count > limit) {
            // getExpire: key 만료까지 남은 초 — 이미 만료됐다면 최소 1초로 보정
            val retryAfter = redisTemplate.getExpire(key, TimeUnit.SECONDS).coerceAtLeast(1L)
            log.warn("[RATE_LIMIT] 회원가입 IP 한도 초과 ip={} retryAfter={}s", ip, retryAfter)
            throw RateLimitException(retryAfter)
        }
    }
}
