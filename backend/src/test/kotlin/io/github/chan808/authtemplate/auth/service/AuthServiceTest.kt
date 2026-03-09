package io.github.chan808.authtemplate.auth.service

import io.github.chan808.authtemplate.auth.api.LoginRequest
import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import io.github.chan808.authtemplate.auth.repository.RefreshTokenStore
import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.security.JwtProvider
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.domain.MemberRole
import io.github.chan808.authtemplate.member.repository.MemberRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val jwtProvider: JwtProvider = mockk()
    private val refreshTokenStore: RefreshTokenStore = mockk()
    private val loginRateLimitService: LoginRateLimitService = mockk()
    private val authService = AuthService(
        memberRepository,
        passwordEncoder,
        jwtProvider,
        refreshTokenStore,
        loginRateLimitService,
    )

    private val member = Member(
        email = "test@example.com",
        password = "encoded-password",
        emailVerified = true,
        role = MemberRole.USER,
        id = 1L,
    )

    @Test
    fun `valid credentials return access token and refresh token`() {
        every { loginRateLimitService.check(any(), any()) } just Runs
        every { memberRepository.findByEmail("test@example.com") } returns member
        every { passwordEncoder.matches(any(), any()) } returns true
        every { jwtProvider.generateAccessToken(1L, "USER") } returns "access-token"
        every { refreshTokenStore.save(any(), any(), any()) } just Runs
        every { refreshTokenStore.addSession(any(), any()) } just Runs

        val (at, rt) = authService.login(LoginRequest("test@example.com", "password123"), "127.0.0.1")

        assertEquals("access-token", at)
        assertTrue(rt.contains('.'))
    }

    @Test
    fun `invalid password throws invalid credentials`() {
        every { loginRateLimitService.check(any(), any()) } just Runs
        every { memberRepository.findByEmail(any()) } returns member
        every { passwordEncoder.matches(any(), any()) } returns false

        val ex = assertThrows<AuthException> {
            authService.login(LoginRequest("test@example.com", "wrong-password"), "127.0.0.1")
        }
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.errorCode)
    }

    @Test
    fun `reissue lock conflict throws reissue conflict`() {
        every { refreshTokenStore.tryLock(any()) } returns false

        val fakeRt = "${UUID.randomUUID()}.randompart"
        val ex = assertThrows<AuthException> { authService.reissue(fakeRt) }

        assertEquals(ErrorCode.REISSUE_CONFLICT, ex.errorCode)
    }

    @Test
    fun `refresh token mismatch revokes session and throws mismatch`() {
        val sid = UUID.randomUUID().toString()
        val session = RefreshTokenSession(
            memberId = 1L,
            role = "USER",
            tokenHash = "wrong-hash",
            absoluteExpiryEpoch = Instant.now().plusSeconds(3600).epochSecond,
        )
        every { refreshTokenStore.tryLock(sid) } returns true
        every { refreshTokenStore.find(sid) } returns session
        every { refreshTokenStore.delete(sid) } just Runs
        every { refreshTokenStore.releaseLock(sid) } just Runs

        val ex = assertThrows<AuthException> { authService.reissue("$sid.actualrandompart") }

        assertEquals(ErrorCode.REFRESH_TOKEN_MISMATCH, ex.errorCode)
        verify { refreshTokenStore.delete(sid) }
    }
}
