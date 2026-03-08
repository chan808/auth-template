package io.github.chan808.authtemplate.member.event

data class MemberRegisteredEvent(
    val email: String,
    val verificationToken: String,
)
