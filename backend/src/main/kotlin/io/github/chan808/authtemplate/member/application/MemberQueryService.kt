package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.member.api.AuthMemberView
import io.github.chan808.authtemplate.member.api.MemberApi
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberQueryService(
    private val memberRepository: MemberRepository,
    private val emailVerificationService: EmailVerificationService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val passwordEncoder: PasswordEncoder,
    private val eventPublisher: ApplicationEventPublisher,
) : MemberApi {

    private val log = LoggerFactory.getLogger(MemberQueryService::class.java)

    override fun findAuthMemberByEmail(email: String): AuthMemberView? =
        memberRepository.findByEmail(email)?.toAuthView()

    override fun findAuthMemberById(id: Long): AuthMemberView? =
        memberRepository.findById(id).orElse(null)?.toAuthView()

    override fun verifyEmail(token: String) {
        emailVerificationService.verify(token)
    }

    @Transactional
    override fun resetPassword(memberId: Long, newRawPassword: String) {
        val member = memberRepository.findById(memberId)
            .orElseThrow { AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID) }
        breachedPasswordChecker.check(newRawPassword, member.email)
        member.changePassword(passwordEncoder.encode(newRawPassword) ?: error("PasswordEncoder returned null"))
        eventPublisher.publishEvent(PasswordChangedEvent(memberId))
    }

    @Transactional
    override fun findOrCreateOAuthMember(
        email: String,
        provider: String,
        providerId: String,
        nickname: String?,
    ): AuthMemberView {
        // provider + providerId로 기존 회원 조회
        memberRepository.findByProviderAndProviderId(provider, providerId)
            ?.let { return it.toAuthView() }

        // 동일 이메일로 가입된 로컬/타 소셜 계정 확인
        memberRepository.findByEmail(email)?.let { existing ->
            val existingProvider = existing.provider ?: "LOCAL"
            log.warn(
                "[AUTH] OAuth 이메일 충돌 email={} existingProvider={} requestedProvider={}",
                email, existingProvider, provider,
            )
            throw AuthException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        // 소셜 최초 가입: 이메일 인증 불필요 (제공자가 이미 검증)
        val member = memberRepository.save(
            Member(
                email = email,
                provider = provider,
                providerId = providerId,
                nickname = nickname,
                emailVerified = true,
            ),
        )
        log.info("[AUTH] OAuth2 신규 가입 provider={} memberId={}", provider, member.id)
        return member.toAuthView()
    }

    private fun Member.toAuthView() = AuthMemberView(
        id = id,
        email = email,
        encodedPassword = password,
        role = role,
        emailVerified = emailVerified,
        provider = provider,
    )
}
