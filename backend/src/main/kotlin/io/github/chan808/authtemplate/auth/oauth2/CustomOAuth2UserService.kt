package io.github.chan808.authtemplate.auth.oauth2

import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomOAuth2UserService(
    private val memberRepository: MemberRepository,
) : DefaultOAuth2UserService() {

    private val log = LoggerFactory.getLogger(CustomOAuth2UserService::class.java)

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val userInfo = oAuth2UserInfoOf(registrationId, oAuth2User.attributes)

        val member = findOrCreate(userInfo)
        log.info("[AUTH] OAuth2 로그인 provider={} memberId={}", userInfo.provider, member.id)

        return CustomOAuth2User(oAuth2User, member.id, userInfo.provider)
    }

    private fun findOrCreate(userInfo: OAuth2UserInfo): Member {
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
        )
    }
}

/** 같은 이메일로 다른 방식으로 이미 가입된 경우 */
class OAuthEmailConflictException(email: String, existingProvider: String) :
    RuntimeException("이미 $existingProvider 방식으로 가입된 이메일입니다: $email")
