package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.member.api.MemberResponse
import io.github.chan808.authtemplate.member.api.SignupRequest
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.MemberRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true) // 기본 readOnly: JPA 스냅샷 비교 생략으로 읽기 성능 개선
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun signup(request: SignupRequest): MemberResponse {
        val email = request.email.lowercase().trim()

        if (memberRepository.existsByEmail(email)) {
            throw MemberException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val member = Member(
            email = email,
            // BCrypt 단방향 해시: 복호화 불가, DB 유출 시 원문 보호
            password = passwordEncoder.encode(request.password) ?: error("PasswordEncoder returned null"),
        )
        return MemberResponse.from(memberRepository.save(member))
    }

    fun getById(memberId: Long): Member =
        memberRepository.findById(memberId).orElseThrow { MemberException(ErrorCode.MEMBER_NOT_FOUND) }

    fun getMyInfo(memberId: Long): MemberResponse = MemberResponse.from(getById(memberId))
}
