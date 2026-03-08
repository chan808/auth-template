package io.github.chan808.authtemplate.auth.service

import io.github.chan808.authtemplate.auth.repository.PasswordResetStore
import io.github.chan808.authtemplate.auth.repository.RefreshTokenStore
import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.mail.MailService
import io.github.chan808.authtemplate.common.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.MemberRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
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
    private val service = PasswordResetService(
        memberRepository, passwordEncoder, passwordResetStore, mailService, breachedPasswordChecker, refreshTokenStore,
    )

    private val member = Member(
        email = "test@example.com",
        password = "encoded-old-password",
        emailVerified = true,
        id = 1L,
    )

    @Test
    fun `가입된 이메일로 requestReset 호출 시 토큰 저장 후 메일을 발송한다`() {
        every { memberRepository.findByEmail("test@example.com") } returns member
        every { passwordResetStore.save(any(), 1L) } just Runs
        every { mailService.sendPasswordResetEmail(any(), any()) } just Runs

        service.requestReset("test@example.com")

        verify(exactly = 1) { passwordResetStore.save(any(), 1L) }
        verify(exactly = 1) { mailService.sendPasswordResetEmail("test@example.com", any()) }
    }

    @Test
    fun `미가입 이메일로 requestReset 호출 시 예외 없이 조용히 반환한다 (열거 공격 방지)`() {
        every { memberRepository.findByEmail(any()) } returns null

        // 예외 없이 정상 종료되어야 하고, 메일 발송도 없어야 함
        service.requestReset("unknown@example.com")

        verify(exactly = 0) { mailService.sendPasswordResetEmail(any(), any()) }
    }

    @Test
    fun `유효한 토큰으로 confirmReset 호출 시 비밀번호가 변경되고 모든 세션이 무효화된다`() {
        every { passwordResetStore.findMemberId("valid-token") } returns 1L
        every { memberRepository.findById(1L) } returns Optional.of(member)
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode("new-password") } returns "encoded-new-password"
        every { passwordResetStore.delete("valid-token") } just Runs
        every { refreshTokenStore.deleteAllSessionsForMember(1L) } just Runs

        service.confirmReset("valid-token", "new-password")

        // 비밀번호 변경 확인
        assertEquals("encoded-new-password", member.password)
        // 토큰 단발성 보장 (재사용 불가)
        verify(exactly = 1) { passwordResetStore.delete("valid-token") }
        // 비밀번호 변경 후 전체 세션 무효화 확인 — 탈취된 세션 강제 로그아웃
        verify(exactly = 1) { refreshTokenStore.deleteAllSessionsForMember(1L) }
    }

    @Test
    fun `만료되거나 존재하지 않는 토큰으로 confirmReset 호출 시 PASSWORD_RESET_TOKEN_INVALID 예외가 발생한다`() {
        every { passwordResetStore.findMemberId("expired-token") } returns null

        val ex = assertThrows<AuthException> { service.confirmReset("expired-token", "new-password") }
        assertEquals(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, ex.errorCode)
    }
}
