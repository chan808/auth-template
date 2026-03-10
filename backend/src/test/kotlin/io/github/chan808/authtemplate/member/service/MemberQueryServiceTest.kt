package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.member.application.EmailVerificationService
import io.github.chan808.authtemplate.member.application.MemberQueryService
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.test.assertEquals

class MemberQueryServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val emailVerificationService: EmailVerificationService = mockk()
    private val breachedPasswordChecker: BreachedPasswordChecker = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val service = MemberQueryService(
        memberRepository,
        emailVerificationService,
        breachedPasswordChecker,
        passwordEncoder,
        eventPublisher,
    )

    @Test
    fun `oauth account cannot reset password`() {
        val oauthMember = Member(
            email = "oauth@example.com",
            provider = "GOOGLE",
            providerId = "123",
            emailVerified = true,
            id = 1L,
        )
        every { memberRepository.findById(1L) } returns Optional.of(oauthMember)

        val ex = assertThrows<AuthException> { service.resetPassword(1L, "NewPass1!") }

        assertEquals(ErrorCode.OAUTH_PASSWORD_RESET_NOT_ALLOWED, ex.errorCode)
        verify(exactly = 0) { breachedPasswordChecker.check(any(), any()) }
    }

    @Test
    fun `local account reset password updates password and publishes event`() {
        val member = Member(
            email = "local@example.com",
            password = "old-password",
            emailVerified = true,
            id = 1L,
        )
        every { memberRepository.findById(1L) } returns Optional.of(member)
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode("NewPass1!") } returns "encoded-password"
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        service.resetPassword(1L, "NewPass1!")

        assertEquals("encoded-password", member.password)
        verify { eventPublisher.publishEvent(any<Any>()) }
    }
}
