package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.events.MemberWithdrawnEvent
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.presentation.ChangePasswordRequest
import io.github.chan808.authtemplate.member.presentation.MemberResponse
import io.github.chan808.authtemplate.member.presentation.SignupRequest
import io.github.chan808.authtemplate.member.presentation.UpdateProfileRequest
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true) // 기본 readOnly: JPA 스냅샷 비교 생략으로 읽기 성능 개선
class MemberCommandService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val signupRateLimitService: SignupRateLimitService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val emailVerificationService: EmailVerificationService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(MemberCommandService::class.java)

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
        member.changePassword(passwordEncoder.encode(request.newPassword) ?: error("PasswordEncoder returned null"))
        // 비밀번호 변경 이벤트 발행 → 모든 기존 세션 무효화 (탈취된 세션 강제 로그아웃)
        eventPublisher.publishEvent(PasswordChangedEvent(memberId))
        log.info("[AUTH] 비밀번호 변경 완료 memberId={}", memberId)
    }

    @Transactional
    fun withdraw(memberId: Long) {
        val member = getById(memberId)
        // 회원 탈퇴 이벤트 발행 → RT 세션 정리 등 후속 처리
        eventPublisher.publishEvent(MemberWithdrawnEvent(memberId))
        memberRepository.delete(member)
        log.info("[MEMBER] 회원 탈퇴 완료 memberId={}", memberId)
    }

    fun getById(memberId: Long): Member =
        memberRepository.findById(memberId).orElseThrow { MemberException(ErrorCode.MEMBER_NOT_FOUND) }

    fun getMyInfo(memberId: Long): MemberResponse = MemberResponse.from(getById(memberId))
}
