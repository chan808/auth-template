package io.github.chan808.authtemplate.auth.oauth2

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

/** SuccessHandler에서 memberId와 provider를 꺼내기 위한 래퍼 */
class CustomOAuth2User(
    private val delegate: OAuth2User,
    val memberId: Long,
    val provider: String,
) : OAuth2User by delegate
