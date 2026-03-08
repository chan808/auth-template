package io.github.chan808.authtemplate.member.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class EmailVerificationStore(private val redisTemplate: StringRedisTemplate) {

    companion object {
        private const val PREFIX = "EMAIL_VERIFY:"
    }

    fun save(token: String, memberId: Long, ttlSeconds: Long) {
        redisTemplate.opsForValue().set("$PREFIX$token", memberId.toString(), ttlSeconds, TimeUnit.SECONDS)
    }

    fun findMemberId(token: String): Long? =
        redisTemplate.opsForValue().get("$PREFIX$token")?.toLongOrNull()

    fun delete(token: String) {
        redisTemplate.delete("$PREFIX$token")
    }
}
