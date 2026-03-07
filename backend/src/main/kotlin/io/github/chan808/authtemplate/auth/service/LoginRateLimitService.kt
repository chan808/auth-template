package io.github.chan808.authtemplate.auth.service

import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.ratelimit.RateLimiter
import org.springframework.stereotype.Service

@Service
class LoginRateLimitService(private val rateLimiter: RateLimiter) {

    fun check(ip: String, email: String) {
        // IP 기준: 사무실/공유망 등 다수 사용자를 감안해 한도를 높게 설정 (password spraying 탐지용)
        if (rateLimiter.isExceeded("RATE:LOGIN:IP:$ip", ttlSeconds = 3600, limit = 20))
            throw AuthException(ErrorCode.TOO_MANY_REQUESTS)

        // 이메일 기준: 한 계정에 집중되는 분산 IP 공격(credential stuffing) 탐지용
        if (rateLimiter.isExceeded("RATE:LOGIN:EMAIL:$email", ttlSeconds = 900, limit = 10))
            throw AuthException(ErrorCode.TOO_MANY_REQUESTS)
    }
}
