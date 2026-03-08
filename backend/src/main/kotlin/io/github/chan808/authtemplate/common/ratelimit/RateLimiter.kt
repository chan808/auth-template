package io.github.chan808.authtemplate.common.ratelimit

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Component
class RateLimiter(private val redisTemplate: StringRedisTemplate) {

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

    // 한도 초과 시 Retry-After에 사용할 남은 TTL(초) 반환, 미초과 시 null 반환
    fun retryAfterIfExceeded(key: String, ttlSeconds: Long, limit: Int): Long? {
        val count = redisTemplate.execute(script, listOf(key), ttlSeconds.toString()) ?: 0L
        if (count <= limit) return null
        // getExpire: key 만료까지 남은 초 — 이미 만료됐다면 최소 1초로 보정
        return redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS).coerceAtLeast(1L)
    }
}
