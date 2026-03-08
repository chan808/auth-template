package io.github.chan808.authtemplate.member.repository

import io.github.chan808.authtemplate.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
    fun findByProviderAndProviderId(provider: String, providerId: String): Member?
}
