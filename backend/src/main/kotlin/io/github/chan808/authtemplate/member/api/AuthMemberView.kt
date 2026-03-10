package io.github.chan808.authtemplate.member.api

import io.github.chan808.authtemplate.member.domain.MemberRole

data class AuthMemberView(
    val id: Long,
    val email: String,
    val encodedPassword: String?,
    val role: MemberRole,
    val emailVerified: Boolean,
    val provider: String?,
) {
    val isOAuthAccount: Boolean get() = provider != null
}
