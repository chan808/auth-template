package io.github.chan808.authtemplate.auth.oauth2

import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * OAuth2 / OIDC 두 흐름에서 공통으로 사용하는 회원 조회/생성 로직
 * CustomOAuth2UserService(Naver, Kakao)와 CustomOidcUserService(Google)가 함께 사용
 */
@Service
class MemberOAuthService(private val memberRepository: MemberRepository) {

    private val log = LoggerFactory.getLogger(MemberOAuthService::class.java)

    @Transactional
    fun findOrCreate(userInfo: OAuth2UserInfo): Member {
        // provider + providerId로 기존 회원 조회
        val existing = memberRepository.findByProviderAndProviderId(userInfo.provider, userInfo.providerId)
        if (existing != null) return existing

        // 동일 이메일로 가입된 로컬/타 소셜 계정 확인
        val emailConflict = memberRepository.findByEmail(userInfo.email)
        if (emailConflict != null) {
            val existingProvider = emailConflict.provider ?: "LOCAL"
            throw OAuthEmailConflictException(userInfo.email, existingProvider)
        }

        // 소셜 최초 가입: 이메일 인증 불필요 (제공자가 이미 검증)
        return memberRepository.save(
            Member(
                email = userInfo.email,
                provider = userInfo.provider,
                providerId = userInfo.providerId,
                emailVerified = true,
            )
        ).also { log.info("[AUTH] OAuth2 신규 가입 provider={} memberId={}", userInfo.provider, it.id) }
    }
}
