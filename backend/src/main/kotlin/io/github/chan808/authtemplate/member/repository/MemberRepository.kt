package io.github.chan808.authtemplate.member.repository

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.domain.MemberRole
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long>, KotlinJdslJpqlExecutor {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
}

// and()의 모든 인자가 null이면 WHERE 절 없이 전체 조회 — 의도된 동작
fun MemberRepository.searchByCondition(email: String?, role: MemberRole?): List<Member> =
    findAll {
        select(entity(Member::class))
        from(entity(Member::class))
        where(
            and(
                email?.let { path(Member::email).like("%$it%") },
                role?.let { path(Member::role).equal(it) },
            )
        )
    }.filterNotNull()
