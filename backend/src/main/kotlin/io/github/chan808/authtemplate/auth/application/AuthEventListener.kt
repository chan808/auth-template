package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.member.events.MemberWithdrawnEvent
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * member 모듈이 발행하는 이벤트를 수신하여 auth 관련 후처리를 수행한다.
 * - PasswordChangedEvent: 비밀번호 변경/재설정 시 모든 세션 무효화
 * - MemberWithdrawnEvent: 회원 탈퇴 시 모든 세션 무효화
 */
@Component
class AuthEventListener(private val tokenStore: TokenStore) {

    private val log = LoggerFactory.getLogger(AuthEventListener::class.java)

    @EventListener
    fun onPasswordChanged(event: PasswordChangedEvent) {
        tokenStore.deleteAllSessionsForMember(event.memberId)
        log.info("[AUTH] 비밀번호 변경으로 전체 세션 무효화 memberId={}", event.memberId)
    }

    @EventListener
    fun onMemberWithdrawn(event: MemberWithdrawnEvent) {
        tokenStore.deleteAllSessionsForMember(event.memberId)
        log.info("[AUTH] 회원 탈퇴로 전체 세션 무효화 memberId={}", event.memberId)
    }
}
