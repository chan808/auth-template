package io.github.chan808.authtemplate.auth.api

import com.ninjasquad.springmockk.MockkBean
import io.github.chan808.authtemplate.auth.repository.OAuthCodeStore
import io.github.chan808.authtemplate.auth.service.AuthService
import io.github.chan808.authtemplate.auth.service.PasswordResetService
import io.github.chan808.authtemplate.common.config.SecurityConfig
import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.RateLimitException
import io.github.chan808.authtemplate.common.security.JwtProvider
import io.github.chan808.authtemplate.common.security.SecurityExceptionHandler
import io.github.chan808.authtemplate.member.service.EmailVerificationService
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import jakarta.servlet.http.Cookie

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
@TestPropertySource(
    properties = [
        "jwt.refresh-token-expiry=604800",
        "cookie.secure=false",
        "cors.allowed-origin=http://localhost:3000",
    ],
)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean lateinit var authService: AuthService
    @MockkBean lateinit var emailVerificationService: EmailVerificationService
    @MockkBean lateinit var passwordResetService: PasswordResetService
    @MockkBean lateinit var oAuthCodeStore: OAuthCodeStore
    @MockkBean lateinit var jwtProvider: JwtProvider
    // SecurityConfig 생성자 주입 대상 — relaxed: commence/handle이 호출돼도 기본 동작 무시
    @MockkBean(relaxed = true) lateinit var securityExceptionHandler: SecurityExceptionHandler

    // ───────────────────────────── /login ─────────────────────────────

    @Test
    fun `로그인 성공 시 AT를 응답 바디에, RT를 HttpOnly 쿠키로 반환한다`() {
        every { authService.login(any(), any()) } returns ("access-token" to "sid.randompart")

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"password123"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("access-token") }
            // RT는 HttpOnly 쿠키로 발급 — 응답 바디에 없어야 함
            cookie { exists("refresh_token") }
            cookie { httpOnly("refresh_token", true) }
        }
    }

    @Test
    fun `이메일 형식이 잘못된 로그인 요청은 400을 반환한다`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"not-an-email","password":"password123"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `잘못된 자격증명으로 로그인하면 401을 반환한다`() {
        every { authService.login(any(), any()) } throws AuthException(ErrorCode.INVALID_CREDENTIALS)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"wrong"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `로그인 요청이 Rate Limit을 초과하면 429와 Retry-After 헤더를 반환한다`() {
        every { authService.login(any(), any()) } throws RateLimitException(retryAfterSeconds = 60L)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"password123"}"""
        }.andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "60") }
        }
    }

    // ───────────────────────────── /reissue ─────────────────────────────

    @Test
    fun `유효한 RT 쿠키와 X-CSRF-GUARD 헤더로 재발급 시 새 AT와 RT를 반환한다`() {
        every { authService.reissue(any()) } returns ("new-access-token" to "new-sid.randompart")

        mockMvc.post("/api/auth/reissue") {
            cookie(Cookie("refresh_token", "old-sid.randompart"))
            header("X-CSRF-GUARD", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("new-access-token") }
            cookie { exists("refresh_token") }
        }
    }

    @Test
    fun `X-CSRF-GUARD 헤더 없이 재발급 요청하면 400을 반환한다`() {
        mockMvc.post("/api/auth/reissue") {
            cookie(Cookie("refresh_token", "sid.randompart"))
            // X-CSRF-GUARD 헤더 의도적으로 누락
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `RT 쿠키 없이 재발급 요청하면 401을 반환한다`() {
        mockMvc.post("/api/auth/reissue") {
            header("X-CSRF-GUARD", "1")
            // RT 쿠키 의도적으로 누락
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    // ───────────────────────────── /logout ─────────────────────────────

    @Test
    fun `로그아웃 성공 시 RT 쿠키 만료 처리(maxAge=0)와 함께 200을 반환한다`() {
        every { authService.logout(any()) } just Runs

        mockMvc.post("/api/auth/logout") {
            cookie(Cookie("refresh_token", "sid.randompart"))
            header("X-CSRF-GUARD", "1")
        }.andExpect {
            status { isOk() }
            // maxAge=0: 브라우저에서 쿠키 즉시 삭제
            cookie { maxAge("refresh_token", 0) }
        }
    }

    @Test
    fun `X-CSRF-GUARD 헤더 없이 로그아웃 요청하면 400을 반환한다`() {
        mockMvc.post("/api/auth/logout") {
            cookie(Cookie("refresh_token", "sid.randompart"))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ───────────────────────────── /verify-email ─────────────────────────────

    @Test
    fun `유효한 토큰으로 이메일 인증 시 200을 반환한다`() {
        every { emailVerificationService.verify("valid-token") } just Runs

        mockMvc.get("/api/auth/verify-email?token=valid-token")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `유효하지 않은 인증 토큰은 400을 반환한다`() {
        every { emailVerificationService.verify("bad-token") } throws
            io.github.chan808.authtemplate.common.exception.MemberException(ErrorCode.VERIFICATION_TOKEN_INVALID)

        mockMvc.get("/api/auth/verify-email?token=bad-token")
            .andExpect { status { isBadRequest() } }
    }

    // ───────────────────────────── /password-reset ─────────────────────────────

    @Test
    fun `미가입 이메일로 비밀번호 재설정 요청해도 200을 반환한다 (열거 공격 방지)`() {
        every { passwordResetService.requestReset(any(), any()) } just Runs

        mockMvc.post("/api/auth/password-reset/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"unknown@example.com"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `유효한 토큰으로 비밀번호 재설정 시 200을 반환한다`() {
        every { passwordResetService.confirmReset(any(), any()) } just Runs

        mockMvc.post("/api/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"valid-token","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `만료된 재설정 토큰은 400을 반환한다`() {
        every { passwordResetService.confirmReset(any(), any()) } throws
            AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)

        mockMvc.post("/api/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"expired-token","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("PASSWORD_RESET_TOKEN_INVALID") }
        }
    }

    // ───────────────────────────── /oauth2/token ─────────────────────────────

    @Test
    fun `유효한 OAuth 코드로 토큰 교환 시 AT를 반환한다`() {
        every { oAuthCodeStore.findAndDelete("valid-code") } returns "access-token"

        mockMvc.get("/api/auth/oauth2/token?code=valid-code")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.accessToken") { value("access-token") }
            }
    }

    @Test
    fun `만료되거나 없는 OAuth 코드는 401을 반환한다`() {
        every { oAuthCodeStore.findAndDelete("expired-code") } returns null

        mockMvc.get("/api/auth/oauth2/token?code=expired-code")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.title") { value("OAUTH_CODE_NOT_FOUND") }
            }
    }
}
