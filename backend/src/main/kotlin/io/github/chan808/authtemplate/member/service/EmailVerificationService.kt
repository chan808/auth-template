package io.github.chan808.authtemplate.member.service

import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import io.github.chan808.authtemplate.member.event.MemberRegisteredEvent
import io.github.chan808.authtemplate.member.repository.EmailVerificationStore
import io.github.chan808.authtemplate.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmailVerificationService(
    private val memberRepository: MemberRepository,
    private val emailVerificationStore: EmailVerificationStore,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(EmailVerificationService::class.java)

    // 호출 측이 @Transactional 범위 내에 있어야 @TransactionalEventListener(AFTER_COMMIT) 정상 발화
    fun sendVerification(memberId: Long, email: String) {
        val token = UUID.randomUUID().toString()
        emailVerificationStore.save(token, memberId, ttlSeconds = 24L * 3600)
        eventPublisher.publishEvent(MemberRegisteredEvent(email, token))
    }

    @Transactional
    fun verify(token: String) {
        val memberId = emailVerificationStore.findMemberId(token) ?: run {
            log.warn("[AUTH] 이메일 인증 실패 reason=INVALID_TOKEN")
            throw MemberException(ErrorCode.VERIFICATION_TOKEN_INVALID)
        }
        val member = memberRepository.findById(memberId)
            .orElseThrow { MemberException(ErrorCode.MEMBER_NOT_FOUND) }
        if (member.emailVerified) {
            log.warn("[AUTH] 이메일 인증 실패 reason=ALREADY_VERIFIED memberId={}", memberId)
            throw MemberException(ErrorCode.EMAIL_ALREADY_VERIFIED)
        }
        member.emailVerified = true
        emailVerificationStore.delete(token)
        log.info("[AUTH] 이메일 인증 완료 memberId={}", memberId)
    }
}
