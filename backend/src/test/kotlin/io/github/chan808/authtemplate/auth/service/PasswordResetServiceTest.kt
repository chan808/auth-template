package io.github.chan808.authtemplate.auth.service

import io.github.chan808.authtemplate.auth.repository.PasswordResetStore
import io.github.chan808.authtemplate.auth.repository.RefreshTokenStore
import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.mail.MailService
import io.github.chan808.authtemplate.common.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.MemberRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.test.assertEquals

class PasswordResetServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val passwordResetStore: PasswordResetStore = mockk()
    private val mailService: MailService = mockk()
    private val breachedPasswordChecker: BreachedPasswordChecker = mockk()
    private val refreshTokenStore: RefreshTokenStore = mockk()
    private val passwordResetRateLimitService: PasswordResetRateLimitService = mockk()
    private val service = PasswordResetService(
        memberRepository,
        passwordEncoder,
        passwordResetStore,
        mailService,
        breachedPasswordChecker,
        refreshTokenStore,
        passwordResetRateLimitService,
    )

    private val member = Member(
        email = "test@example.com",
        password = "encoded-old-password",
        emailVerified = true,
        id = 1L,
    )

    @Test
    fun `request reset stores token and sends email for existing member`() {
        every { passwordResetRateLimitService.check(any(), any()) } just Runs
        every { memberRepository.findByEmail("test@example.com") } returns member
        every { passwordResetStore.save(any(), 1L) } just Runs
        every { mailService.sendPasswordResetEmail(any(), any()) } just Runs

        service.requestReset("test@example.com", "127.0.0.1")

        verify(exactly = 1) { passwordResetRateLimitService.check("127.0.0.1", "test@example.com") }
        verify(exactly = 1) { passwordResetStore.save(any(), 1L) }
        verify(exactly = 1) { mailService.sendPasswordResetEmail("test@example.com", any()) }
    }

    @Test
    fun `request reset on unknown email returns silently`() {
        every { passwordResetRateLimitService.check(any(), any()) } just Runs
        every { memberRepository.findByEmail(any()) } returns null

        service.requestReset("unknown@example.com", "127.0.0.1")

        verify(exactly = 1) { passwordResetRateLimitService.check("127.0.0.1", "unknown@example.com") }
        verify(exactly = 0) { mailService.sendPasswordResetEmail(any(), any()) }
    }

    @Test
    fun `confirm reset updates password and invalidates all sessions`() {
        every { passwordResetStore.findMemberId("valid-token") } returns 1L
        every { memberRepository.findById(1L) } returns Optional.of(member)
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode("new-password") } returns "encoded-new-password"
        every { passwordResetStore.delete("valid-token") } just Runs
        every { refreshTokenStore.deleteAllSessionsForMember(1L) } just Runs

        service.confirmReset("valid-token", "new-password")

        assertEquals("encoded-new-password", member.password)
        verify(exactly = 1) { passwordResetStore.delete("valid-token") }
        verify(exactly = 1) { refreshTokenStore.deleteAllSessionsForMember(1L) }
    }

    @Test
    fun `confirm reset with invalid token throws exception`() {
        every { passwordResetStore.findMemberId("expired-token") } returns null

        val ex = assertThrows<AuthException> { service.confirmReset("expired-token", "new-password") }
        assertEquals(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, ex.errorCode)
    }
}
