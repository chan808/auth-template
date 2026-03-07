package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.common.ratelimit.RateLimiter
import org.springframework.stereotype.Service

@Service
class SignupRateLimitService(private val rateLimiter: RateLimiter) {

    fun check(ip: String) {
        if (rateLimiter.isExceeded("RATE:SIGNUP:IP:$ip", ttlSeconds = 3600, limit = 5))
            throw MemberException(ErrorCode.TOO_MANY_REQUESTS)
    }
}
