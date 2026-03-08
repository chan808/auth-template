package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.EmailVerificationStore
import io.github.chan808.authtemplate.member.repository.MemberRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailVerificationServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val emailVerificationStore: EmailVerificationStore = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val service = EmailVerificationService(memberRepository, emailVerificationStore, eventPublisher)

    private val member = Member(
        email = "test@example.com",
        emailVerified = false,
        id = 1L,
    )

    @Test
    fun `sendVerification 호출 시 토큰이 저장되고 이벤트가 발행된다`() {
        every { emailVerificationStore.save(any(), 1L, any()) } just Runs
        every { eventPublisher.publishEvent(any()) } just Runs

        service.sendVerification(1L, "test@example.com")

        // 토큰이 저장됐는지, 이벤트가 정확히 1회 발행됐는지 확인
        verify(exactly = 1) { emailVerificationStore.save(any(), 1L, any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `유효한 토큰으로 verify 호출 시 emailVerified가 true로 변경된다`() {
        every { emailVerificationStore.findMemberId("valid-token") } returns 1L
        every { memberRepository.findById(1L) } returns Optional.of(member)
        every { emailVerificationStore.delete("valid-token") } just Runs

        service.verify("valid-token")

        assertTrue(member.emailVerified)
        verify(exactly = 1) { emailVerificationStore.delete("valid-token") }
    }

    @Test
    fun `존재하지 않는 토큰으로 verify 호출 시 VERIFICATION_TOKEN_INVALID 예외가 발생한다`() {
        every { emailVerificationStore.findMemberId("invalid-token") } returns null

        val ex = assertThrows<MemberException> { service.verify("invalid-token") }
        assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, ex.errorCode)
    }

    @Test
    fun `이미 인증된 이메일로 verify 호출 시 EMAIL_ALREADY_VERIFIED 예외가 발생한다`() {
        val verifiedMember = Member(email = "test@example.com", emailVerified = true, id = 1L)
        every { emailVerificationStore.findMemberId("token") } returns 1L
        every { memberRepository.findById(1L) } returns Optional.of(verifiedMember)

        val ex = assertThrows<MemberException> { service.verify("token") }
        assertEquals(ErrorCode.EMAIL_ALREADY_VERIFIED, ex.errorCode)
    }
}
