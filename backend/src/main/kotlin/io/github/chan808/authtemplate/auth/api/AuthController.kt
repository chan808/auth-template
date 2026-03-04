package io.github.chan808.authtemplate.auth.api

import io.github.chan808.authtemplate.auth.service.AuthService
import io.github.chan808.authtemplate.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val (at, rt) = authService.login(request)
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie(rt, RT_TTL).toString())
        return ResponseEntity.ok(ApiResponse.of(TokenResponse(at)))
    }

    // X-CSRF-GUARD: SameSite=Strict와 함께 이중 방어 — 브라우저 폼은 커스텀 헤더를 설정할 수 없음
    @PostMapping("/reissue")
    fun reissue(
        @CookieValue("refresh_token") rtToken: String,
        @RequestHeader("X-CSRF-GUARD") csrfGuard: String,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val (at, newRt) = authService.reissue(rtToken)
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie(newRt, RT_TTL).toString())
        return ResponseEntity.ok(ApiResponse.of(TokenResponse(at)))
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue("refresh_token", required = false) rtToken: String?,
        @RequestHeader("X-CSRF-GUARD") csrfGuard: String,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.logout(rtToken)
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie("", 0).toString())
        return ResponseEntity.ok(ApiResponse.success())
    }

    private fun buildRtCookie(value: String, maxAge: Long): ResponseCookie =
        ResponseCookie.from(RT_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(true) // 로컬 개발 시 false로 변경 또는 HTTPS 환경 필요
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(maxAge)
            .build()

    companion object {
        private const val RT_COOKIE_NAME = "refresh_token"
        private const val RT_TTL = 7L * 24 * 3600
    }
}
