package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.member.api.SignupRequest
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.MemberRepository
import io.mockk.every
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
    private val memberService = MemberService(memberRepository, passwordEncoder)

    @Test
    fun `이미 존재하는 이메일로 가입하면 EMAIL_ALREADY_EXISTS 예외가 발생한다`() {
        every { memberRepository.existsByEmail(any()) } returns true

        val ex = assertThrows<MemberException> {
            memberService.signup(SignupRequest("test@example.com", "password123"))
        }
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.errorCode)
    }

    @Test
    fun `이메일은 소문자로 정규화되어 저장된다`() {
        every { memberRepository.existsByEmail("test@example.com") } returns false
        every { passwordEncoder.encode(any()) } returns "encoded"
        every { memberRepository.save(any()) } answers { firstArg<Member>().also { } }

        memberService.signup(SignupRequest("TEST@EXAMPLE.COM", "password123"))

        verify { memberRepository.save(match { it.email == "test@example.com" }) }
    }

    @Test
    fun `존재하지 않는 memberId 조회 시 MEMBER_NOT_FOUND 예외가 발생한다`() {
        every { memberRepository.findById(999L) } returns Optional.empty()

        val ex = assertThrows<MemberException> { memberService.getById(999L) }
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.errorCode)
    }
}
