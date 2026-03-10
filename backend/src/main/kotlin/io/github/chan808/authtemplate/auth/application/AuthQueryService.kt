package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProvider
import io.jsonwebtoken.JwtException
import org.springframework.stereotype.Service

/**
 * 토큰 검증 등 읽기 전용 auth 연산을 담당한다.
 */
@Service
class AuthQueryService(private val jwtProvider: JwtProvider) {

    /**
     * 토큰이 유효하면 memberId를 반환하고, 유효하지 않으면 null을 반환한다.
     */
    fun validateToken(token: String): Long? =
        try {
            jwtProvider.getMemberId(token)
        } catch (_: JwtException) {
            null
        }
}
