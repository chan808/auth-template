package io.github.chan808.authtemplate.member.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.domain.MemberRole
import io.github.chan808.authtemplate.member.domain.QMember

class MemberRepositoryImpl(private val queryFactory: JPAQueryFactory) : MemberRepositoryCustom {

    // Querydsl null-safe 동적 쿼리: null 조건은 where()에서 자동 무시
    override fun searchByCondition(email: String?, role: MemberRole?): List<Member> =
        queryFactory
            .selectFrom(QMember.member)
            .where(
                email?.let { QMember.member.email.containsIgnoreCase(it) },
                role?.let { QMember.member.role.eq(it) },
            )
            .fetch()
}
