package io.github.chan808.authtemplate.auth.service

import io.github.chan808.authtemplate.auth.application.PasswordResetRateLimitService
import io.github.chan808.authtemplate.auth.application.PasswordResetService
import io.github.chan808.authtemplate.auth.infrastructure.redis.PasswordResetStore
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.auth.application.port.AuthMailSender
import io.github.chan808.authtemplate.member.api.AuthMemberView
import io.github.chan808.authtemplate.member.api.MemberApi
import io.github.chan808.authtemplate.member.domain.MemberRole
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PasswordResetServiceTest {

    private val memberApi: MemberApi = mockk()
    private val passwordResetStore: PasswordResetStore = mockk()
    private val mailSender: AuthMailSender = mockk()
    private val passwordResetRateLimitService: PasswordResetRateLimitService = mockk()
    private val service = PasswordResetService(
        memberApi,
        passwordResetStore,
        mailSender,
        passwordResetRateLimitService,
        "https://example.com",
    )

    private val memberView = AuthMemberView(
        id = 1L,
        email = "test@example.com",
        encodedPassword = "encoded-old-password",
        role = MemberRole.USER,
        emailVerified = true,
        provider = null,
    )

    @Test
    fun `request reset stores token and sends email for existing member`() {
        every { passwordResetRateLimitService.check(any(), any()) } just Runs
        every { memberApi.findAuthMemberByEmail("test@example.com") } returns memberView
        every { passwordResetStore.save(any(), 1L) } just Runs
        every { mailSender.send(any(), any(), any()) } just Runs

        service.requestReset("test@example.com", "127.0.0.1")

        verify(exactly = 1) { passwordResetRateLimitService.check("127.0.0.1", "test@example.com") }
        verify(exactly = 1) { passwordResetStore.save(any(), 1L) }
        verify(exactly = 1) { mailSender.send("test@example.com", "비밀번호 재설정", any()) }
    }

    @Test
    fun `request reset on unknown email returns silently`() {
        every { passwordResetRateLimitService.check(any(), any()) } just Runs
        every { memberApi.findAuthMemberByEmail(any()) } returns null

        service.requestReset("unknown@example.com", "127.0.0.1")

        verify(exactly = 1) { passwordResetRateLimitService.check("127.0.0.1", "unknown@example.com") }
        verify(exactly = 0) { mailSender.send(any(), any(), any()) }
    }

    @Test
    fun `confirm reset delegates to memberApi and deletes token`() {
        every { passwordResetStore.findMemberId("valid-token") } returns 1L
        every { memberApi.resetPassword(1L, "new-password") } just Runs
        every { passwordResetStore.delete("valid-token") } just Runs

        service.confirmReset("valid-token", "new-password")

        verify(exactly = 1) { memberApi.resetPassword(1L, "new-password") }
        verify(exactly = 1) { passwordResetStore.delete("valid-token") }
    }

    @Test
    fun `confirm reset with invalid token throws exception`() {
        every { passwordResetStore.findMemberId("expired-token") } returns null

        val ex = assertThrows<AuthException> { service.confirmReset("expired-token", "new-password") }
        assertEquals(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, ex.errorCode)
    }
}
