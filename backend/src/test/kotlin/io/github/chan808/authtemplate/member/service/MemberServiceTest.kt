package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.auth.repository.RefreshTokenStore
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.common.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.api.SignupRequest
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

class MemberServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val signupRateLimitService: SignupRateLimitService = mockk()
    private val breachedPasswordChecker: BreachedPasswordChecker = mockk()
    private val emailVerificationService: EmailVerificationService = mockk()
    private val refreshTokenStore: RefreshTokenStore = mockk()
    private val memberService = MemberService(
        memberRepository,
        passwordEncoder,
        signupRateLimitService,
        breachedPasswordChecker,
        emailVerificationService,
        refreshTokenStore,
    )

    @Test
    fun `duplicate email throws email already exists`() {
        every { signupRateLimitService.check(any()) } just Runs
        every { memberRepository.existsByEmail(any()) } returns true

        val ex = assertThrows<MemberException> {
            memberService.signup(SignupRequest("test@example.com", "Password1!"), "127.0.0.1")
        }
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.errorCode)
    }

    @Test
    fun `email is normalized to lowercase on signup`() {
        every { signupRateLimitService.check(any()) } just Runs
        every { memberRepository.existsByEmail("test@example.com") } returns false
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode(any()) } returns "encoded"
        every { memberRepository.save(any()) } answers {
            firstArg<Member>().let { it.copyForTest(id = 1L) }
        }
        every { emailVerificationService.sendVerification(any(), any()) } just Runs

        memberService.signup(SignupRequest("TEST@EXAMPLE.COM", "Password1!"), "127.0.0.1")

        verify { memberRepository.save(match { it.email == "test@example.com" }) }
    }

    @Test
    fun `missing member id throws member not found`() {
        every { memberRepository.findById(999L) } returns Optional.empty()

        val ex = assertThrows<MemberException> { memberService.getById(999L) }
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.errorCode)
    }

    /**
     * Test helper because Member is immutable for id.
     */
    private fun Member.copyForTest(id: Long): Member = Member(
        email = this.email,
        password = this.password,
        emailVerified = this.emailVerified,
        provider = this.provider,
        providerId = this.providerId,
        nickname = this.nickname,
        role = this.role,
        id = id,
    )
}
