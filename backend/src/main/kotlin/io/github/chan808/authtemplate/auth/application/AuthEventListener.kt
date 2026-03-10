package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.events.MemberWithdrawnEvent
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AuthEventListener(
    private val tokenStore: TokenStore,
    private val domainMetrics: DomainMetrics,
) {

    private val log = LoggerFactory.getLogger(AuthEventListener::class.java)

    @EventListener
    fun onPasswordChanged(event: PasswordChangedEvent) {
        tokenStore.deleteAllSessionsForMember(event.memberId)
        domainMetrics.recordSessionInvalidation("password_changed")
        log.info("[AUTH] invalidated all sessions after password change memberId={}", event.memberId)
    }

    @EventListener
    fun onMemberWithdrawn(event: MemberWithdrawnEvent) {
        tokenStore.deleteAllSessionsForMember(event.memberId)
        domainMetrics.recordSessionInvalidation("member_withdrawn")
        log.info("[AUTH] invalidated all sessions after member withdrawal memberId={}", event.memberId)
    }
}
