package io.github.chan808.authtemplate.auth.oauth2

import io.github.chan808.authtemplate.auth.repository.OAuthCodeStore
import io.github.chan808.authtemplate.auth.service.AuthService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OAuth2SuccessHandler(
    private val authService: AuthService,
    private val oAuthCodeStore: OAuthCodeStore,
    @Value("\${app.base-url}") private val frontendBaseUrl: String,
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
    @Value("\${jwt.refresh-token-expiry}") private val rtExpiry: Long,
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(OAuth2SuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2User = authentication.principal as AuthenticatedOAuth2User
        val (accessToken, rawRt) = authService.issueTokensForOAuth(oAuth2User.memberId)

        // RT: HttpOnly 쿠키
        response.addCookie(Cookie("refresh_token", rawRt).apply {
            isHttpOnly = true
            secure = cookieSecure
            path = "/api/auth"
            maxAge = rtExpiry.toInt()
            setAttribute("SameSite", "Strict")
        })

        // AT: one-time code로 프론트엔드에 전달 (URL 직접 노출 방지, TTL 60초)
        val code = UUID.randomUUID().toString()
        oAuthCodeStore.save(code, accessToken)

        log.info("[AUTH] OAuth2 로그인 성공 memberId={} provider={}", oAuth2User.memberId, oAuth2User.provider)
        response.sendRedirect("$frontendBaseUrl/auth/callback?code=$code")
    }
}
