package io.github.chan808.authtemplate.member.api

import com.ninja_squad.springmockk.MockkBean
import io.github.chan808.authtemplate.common.config.SecurityConfig
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.common.exception.RateLimitException
import io.github.chan808.authtemplate.common.security.JwtProvider
import io.github.chan808.authtemplate.common.security.SecurityExceptionHandler
import io.github.chan808.authtemplate.member.service.MemberService
import io.jsonwebtoken.Claims
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
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
@Import(SecurityConfig::class)
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
    @MockkBean(relaxed = true) lateinit var securityExceptionHandler: SecurityExceptionHandler

    private val testMemberResponse = MemberResponse(
        id = 1L,
        email = "test@example.com",
        nickname = "테스터",
        provider = null,
        role = "USER",
        createdAt = LocalDateTime.now(),
    )

    // 인증이 필요한 테스트에서 Bearer 토큰 헤더와 함께 사용
    // JwtAuthenticationFilter가 이 토큰을 검증해 memberId=1L 로 SecurityContext를 설정
    private val authHeader = "Bearer test-token"

    @BeforeEach
    fun setupJwtMock() {
        val claims = mockk<Claims>()
        every { claims.subject } returns "1"
        every { claims["role"] } returns "USER"
        every { jwtProvider.validate("test-token") } returns claims
    }

    // ───────────────────────────── POST /api/members (회원가입) ─────────────────────────────

    @Test
    fun `정상 입력으로 회원가입 시 201을 반환하고 회원 정보를 응답한다`() {
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
    fun `이미 사용 중인 이메일로 가입하면 409를 반환한다`() {
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
    fun `비밀번호 형식이 잘못된 가입 요청은 400을 반환한다`() {
        // 영문, 숫자, 특수문자 중 특수문자 없음
        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"onlyletters123"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `회원가입 Rate Limit 초과 시 429와 Retry-After 헤더를 반환한다`() {
        every { memberService.signup(any(), any()) } throws RateLimitException(retryAfterSeconds = 3600L)

        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"Password1!"}"""
        }.andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "3600") }
        }
    }

    // ───────────────────────────── GET /api/members/me ─────────────────────────────

    @Test
    fun `인증된 사용자가 내 정보를 조회하면 200과 회원 정보를 반환한다`() {
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
    fun `인증 없이 내 정보 조회하면 401을 반환한다`() {
        mockMvc.get("/api/members/me")
            // Authorization 헤더 없음
            .andExpect { status { isUnauthorized() } }
    }

    // ───────────────────────────── PATCH /api/members/me/profile ─────────────────────────────

    @Test
    fun `인증된 사용자가 닉네임을 수정하면 200과 업데이트된 정보를 반환한다`() {
        val updated = testMemberResponse.copy(nickname = "새닉네임")
        every { memberService.updateProfile(1L, any()) } returns updated

        mockMvc.patch("/api/members/me/profile") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"새닉네임"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.nickname") { value("새닉네임") }
        }
    }

    @Test
    fun `닉네임이 50자를 초과하면 400을 반환한다`() {
        val longNickname = "a".repeat(51)

        mockMvc.patch("/api/members/me/profile") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"$longNickname"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ───────────────────────────── PATCH /api/members/me/password ─────────────────────────────

    @Test
    fun `현재 비밀번호가 일치하면 비밀번호 변경에 성공하고 200을 반환한다`() {
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
    fun `현재 비밀번호가 틀리면 400을 반환한다`() {
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
    fun `새 비밀번호 형식이 잘못되면 서비스 호출 없이 400을 반환한다`() {
        // 특수문자 없는 비밀번호: Bean Validation 단계에서 차단되어야 함
        mockMvc.patch("/api/members/me/password") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"OldPass1!","newPassword":"onlyletters123"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ───────────────────────────── DELETE /api/members/me ─────────────────────────────

    @Test
    fun `회원 탈퇴 성공 시 200을 반환하고 RT 쿠키를 만료 처리한다`() {
        every { memberService.withdraw(1L) } just Runs

        mockMvc.delete("/api/members/me") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            // maxAge=0: 브라우저에서 RT 쿠키 즉시 삭제
            cookie { maxAge("refresh_token", 0) }
        }
    }

    @Test
    fun `인증 없이 회원 탈퇴 시도하면 401을 반환한다`() {
        mockMvc.delete("/api/members/me")
            .andExpect { status { isUnauthorized() } }
    }
}
