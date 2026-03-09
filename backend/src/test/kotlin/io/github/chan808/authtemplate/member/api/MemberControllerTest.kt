package io.github.chan808.authtemplate.member.api

import com.ninjasquad.springmockk.MockkBean
import io.github.chan808.authtemplate.common.config.SecurityConfig
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.common.exception.RateLimitException
import io.github.chan808.authtemplate.common.security.JwtProvider
import io.github.chan808.authtemplate.common.security.SecurityExceptionHandler
import io.github.chan808.authtemplate.member.service.MemberService
import io.jsonwebtoken.Claims
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(MemberController::class)
@Import(SecurityConfig::class, SecurityExceptionHandler::class)
@TestPropertySource(
    properties = [
        "jwt.refresh-token-expiry=604800",
        "cookie.secure=false",
        "cors.allowed-origin=http://localhost:3000",
    ],
)
class MemberControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean lateinit var memberService: MemberService
    @MockkBean lateinit var jwtProvider: JwtProvider
    private val testMemberResponse = MemberResponse(
        id = 1L,
        email = "test@example.com",
        nickname = "tester",
        provider = null,
        role = "USER",
        createdAt = LocalDateTime.now(),
    )

    private val authHeader = "Bearer test-token"

    @BeforeEach
    fun setupJwtMock() {
        val claims = mockk<Claims>()
        every { claims.subject } returns "1"
        every { claims["role"] } returns "USER"
        every { jwtProvider.validate("test-token") } returns claims
    }

    @Test
    fun `signup returns 201`() {
        every { memberService.signup(any(), any()) } returns testMemberResponse

        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"Password1!"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.email") { value("test@example.com") }
        }
    }

    @Test
    fun `duplicate email returns 409`() {
        every { memberService.signup(any(), any()) } throws MemberException(ErrorCode.EMAIL_ALREADY_EXISTS)

        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"Password1!"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.title") { value("EMAIL_ALREADY_EXISTS") }
        }
    }

    @Test
    fun `invalid password format returns 400`() {
        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"onlyletters123"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `signup rate limit returns 429 with retry after`() {
        every { memberService.signup(any(), any()) } throws RateLimitException(retryAfterSeconds = 3600L)

        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"Password1!"}"""
        }.andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "3600") }
        }
    }

    @Test
    fun `get my info returns 200`() {
        every { memberService.getMyInfo(1L) } returns testMemberResponse

        mockMvc.get("/api/members/me") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.email") { value("test@example.com") }
            jsonPath("$.data.role") { value("USER") }
        }
    }

    @Test
    fun `get my info without auth returns 401`() {
        mockMvc.get("/api/members/me")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `update profile returns 200`() {
        val updated = testMemberResponse.copy(nickname = "new-nickname")
        every { memberService.updateProfile(1L, any()) } returns updated

        mockMvc.patch("/api/members/me/profile") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"new-nickname"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.nickname") { value("new-nickname") }
        }
    }

    @Test
    fun `too long nickname returns 400`() {
        val longNickname = "a".repeat(51)

        mockMvc.patch("/api/members/me/profile") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"$longNickname"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `change password returns 200`() {
        every { memberService.changePassword(1L, any()) } just Runs

        mockMvc.patch("/api/members/me/password") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"OldPass1!","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `invalid current password returns 400`() {
        every { memberService.changePassword(1L, any()) } throws MemberException(ErrorCode.INVALID_CURRENT_PASSWORD)

        mockMvc.patch("/api/members/me/password") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"WrongPass1!","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("INVALID_CURRENT_PASSWORD") }
        }
    }

    @Test
    fun `withdraw returns 200 and expires refresh token cookie`() {
        every { memberService.withdraw(1L) } just Runs

        mockMvc.delete("/api/members/me") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            cookie { maxAge("refresh_token", 0) }
        }
    }

    @Test
    fun `withdraw without auth returns 401`() {
        mockMvc.delete("/api/members/me")
            .andExpect { status { isUnauthorized() } }
    }
}
