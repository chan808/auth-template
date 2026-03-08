package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.auth.repository.RefreshTokenStore
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.common.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.api.ChangePasswordRequest
import io.github.chan808.authtemplate.member.api.MemberResponse
import io.github.chan808.authtemplate.member.api.SignupRequest
import io.github.chan808.authtemplate.member.api.UpdateProfileRequest
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.MemberRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true) // 기본 readOnly: JPA 스냅샷 비교 생략으로 읽기 성능 개선
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val signupRateLimitService: SignupRateLimitService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val emailVerificationService: EmailVerificationService,
    private val refreshTokenStore: RefreshTokenStore,
) {
    private val log = LoggerFactory.getLogger(MemberService::class.java)

    @Transactional
    fun signup(request: SignupRequest, ip: String): MemberResponse {
        signupRateLimitService.check(ip)
        val email = request.email.lowercase().trim()

        if (memberRepository.existsByEmail(email)) {
            throw MemberException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        // NIST SP 800-63B 5.1.1.2: 유출 비밀번호 + context-specific words 차단
        breachedPasswordChecker.check(request.password, email)

        val member = memberRepository.save(
            Member(
                email = email,
                // BCrypt 단방향 해시: 복호화 불가, DB 유출 시 원문 보호
                password = passwordEncoder.encode(request.password) ?: error("PasswordEncoder returned null"),
            ),
        )
        // @TransactionalEventListener(AFTER_COMMIT) → 트랜잭션 성공 후에만 인증 메일 발송
        emailVerificationService.sendVerification(member.id, member.email)
        return MemberResponse.from(member)
    }

    @Transactional
    fun updateProfile(memberId: Long, request: UpdateProfileRequest): MemberResponse {
        val member = getById(memberId)
        member.updateProfile(request.nickname)
        log.info("[MEMBER] 프로필 수정 memberId={}", memberId)
        return MemberResponse.from(member)
    }

    @Transactional
    fun changePassword(memberId: Long, request: ChangePasswordRequest) {
        val member = getById(memberId)
        if (!passwordEncoder.matches(request.currentPassword, member.password)) {
            throw MemberException(ErrorCode.INVALID_CURRENT_PASSWORD)
        }
        breachedPasswordChecker.check(request.newPassword, member.email)
        member.password = passwordEncoder.encode(request.newPassword) ?: error("PasswordEncoder returned null")
        // 비밀번호 변경 후 모든 기존 세션 무효화 → 탈취된 세션 강제 로그아웃
        refreshTokenStore.deleteAllSessionsForMember(memberId)
        log.info("[AUTH] 비밀번호 변경 완료 memberId={}", memberId)
    }

    @Transactional
    fun withdraw(memberId: Long) {
        val member = getById(memberId)
        // 모든 RT 세션 먼저 정리 → 이후 AT로 재요청 불가
        refreshTokenStore.deleteAllSessionsForMember(memberId)
        memberRepository.delete(member)
        log.info("[MEMBER] 회원 탈퇴 완료 memberId={}", memberId)
    }

    fun getById(memberId: Long): Member =
        memberRepository.findById(memberId).orElseThrow { MemberException(ErrorCode.MEMBER_NOT_FOUND) }

    fun getMyInfo(memberId: Long): MemberResponse = MemberResponse.from(getById(memberId))
}
