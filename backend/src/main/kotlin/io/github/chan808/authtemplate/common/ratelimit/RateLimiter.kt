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

    fun isExceeded(key: String, ttlSeconds: Long, limit: Int): Boolean =
        (redisTemplate.execute(script, listOf(key), ttlSeconds.toString()) ?: 0L) > limit
}
