package io.github.chan808.authtemplate.member.repository

import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.domain.MemberRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean

    // and()의 모든 인자가 null이면 WHERE 절 없이 전체 조회 — 의도된 동작
    @Query("""
        SELECT m FROM Member m
        WHERE (:email IS NULL OR m.email LIKE %:email%)
          AND (:role IS NULL OR m.role = :role)
    """)
    fun searchByCondition(email: String?, role: MemberRole?): List<Member>
}
