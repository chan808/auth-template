package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.member.application.port.MailSender
import io.github.chan808.authtemplate.member.domain.event.MemberRegisteredEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MemberEventListener(
    private val mailSender: MailSender,
    @Value("\${app.base-url}") private val baseUrl: String,
) {

    // AFTER_COMMIT: 트랜잭션 롤백 시 메일 미발송 보장
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMemberRegistered(event: MemberRegisteredEvent) {
        val verificationLink = "$baseUrl/api/auth/verify-email?token=${event.verificationToken}"
        val body = """
            이메일 인증을 완료하려면 아래 링크를 클릭해주세요.

            $verificationLink

            이 링크는 24시간 동안 유효합니다.
        """.trimIndent()
        mailSender.send(event.email, "이메일 인증", body)
    }
}
