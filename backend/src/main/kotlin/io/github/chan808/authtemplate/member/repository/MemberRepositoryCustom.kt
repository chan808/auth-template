package io.github.chan808.authtemplate.member.repository

import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.domain.MemberRole

interface MemberRepositoryCustom {
    // Querydsl 동적 조건 조합: null 파라미터는 자동으로 WHERE 절에서 제외됨
    fun searchByCondition(email: String?, role: MemberRole?): List<Member>
}
