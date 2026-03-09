package io.github.chan808.authtemplate.auth.api

import io.github.chan808.authtemplate.auth.repository.OAuthCodeStore
import io.github.chan808.authtemplate.auth.service.AuthService
import io.github.chan808.authtemplate.auth.service.PasswordResetService
import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.response.ApiResponse
import io.github.chan808.authtemplate.common.web.clientIp
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
    private val oAuthCodeStore: OAuthCodeStore,
    // 로컬 HTTP 개발 시 false — 운영 HTTPS에서는 COOKIE_SECURE=true 환경변수로 설정
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
    // RT 쿠키 Max-Age: application.yml의 jwt.refresh-token-expiry와 단일 출처로 동기화
    @Value("\${jwt.refresh-token-expiry}") private val rtTtl: Long,
) {

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        servletRequest: HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val (at, rt) = authService.login(request, servletRequest.clientIp())
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie(rt, rtTtl).toString())
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
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie(newRt, rtTtl).toString())
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
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        passwordResetService.requestReset(request.email, servletRequest.clientIp())
        return ResponseEntity.ok(ApiResponse.success())
    }

    /** OAuth 로그인 후 AT를 one-time code로 교환 (AT를 URL에 직접 노출하지 않기 위함) */
    @GetMapping("/oauth2/token")
    fun exchangeOAuthCode(@RequestParam code: String): ResponseEntity<ApiResponse<TokenResponse>> {
        val accessToken = oAuthCodeStore.findAndDelete(code)
            ?: throw AuthException(ErrorCode.OAUTH_CODE_NOT_FOUND)
        return ResponseEntity.ok(ApiResponse.of(TokenResponse(accessToken)))
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
    }
}
