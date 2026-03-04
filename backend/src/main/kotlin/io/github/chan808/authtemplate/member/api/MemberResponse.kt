package io.github.chan808.authtemplate.member.api

import io.github.chan808.authtemplate.member.domain.Member
import java.time.LocalDateTime

data class MemberResponse(
    val id: Long,
    val email: String,
    val role: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(member: Member): MemberResponse = MemberResponse(
            id = member.id,
            email = member.email,
            role = member.role.name,
            createdAt = member.createdAt,
        )
    }
}
