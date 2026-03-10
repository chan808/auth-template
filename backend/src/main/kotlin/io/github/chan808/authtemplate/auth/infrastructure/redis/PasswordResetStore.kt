package io.github.chan808.authtemplate.auth.infrastructure.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class PasswordResetStore(private val redisTemplate: StringRedisTemplate) {

    companion object {
        private const val PREFIX = "RESET:"
        private const val TTL_SECONDS = 1800L // 30분
    }

    fun save(token: String, memberId: Long) {
        redisTemplate.opsForValue().set("$PREFIX$token", memberId.toString(), TTL_SECONDS, TimeUnit.SECONDS)
    }

    fun findMemberId(token: String): Long? =
        redisTemplate.opsForValue().get("$PREFIX$token")?.toLongOrNull()

    fun delete(token: String) {
        redisTemplate.delete("$PREFIX$token")
    }
}
