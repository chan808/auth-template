package io.github.chan808.authtemplate.auth.api

import io.github.chan808.authtemplate.auth.service.AuthService
import io.github.chan808.authtemplate.auth.service.PasswordResetService
import io.github.chan808.authtemplate.common.response.ApiResponse
import io.github.chan808.authtemplate.member.service.EmailVerificationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
    // 로컬 HTTP 개발 시 false — 운영 HTTPS에서는 COOKIE_SECURE=true 환경변수로 설정
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
) {

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        servletRequest: HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val (at, rt) = authService.login(request, servletRequest.clientIp())
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

    @GetMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<ApiResponse<Unit>> {
        emailVerificationService.verify(token)
        return ResponseEntity.ok(ApiResponse.success())
    }

    // 이메일 존재 여부와 무관하게 200 반환 → enumeration attack 방지
    @PostMapping("/password-reset/request")
    fun requestPasswordReset(
        @RequestBody @Valid request: PasswordResetRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        passwordResetService.requestReset(request.email)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(
        @RequestBody @Valid request: PasswordResetConfirmRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        passwordResetService.confirmReset(request.token, request.newPassword)
        return ResponseEntity.ok(ApiResponse.success())
    }

    private fun buildRtCookie(value: String, maxAge: Long): ResponseCookie =
        ResponseCookie.from(RT_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(maxAge)
            .build()

    companion object {
        private const val RT_COOKIE_NAME = "refresh_token"
        private const val RT_TTL = 7L * 24 * 3600
    }
}

// X-Forwarded-For: 역방향 프록시(nginx 등) 뒤에서 실제 클라이언트 IP 추출
// 운영 환경에서는 nginx가 신뢰된 헤더만 전달하도록 설정 필요 (헤더 스푸핑 방지)
private fun HttpServletRequest.clientIp(): String =
    getHeader("X-Forwarded-For")?.split(",")?.first()?.trim() ?: remoteAddr
