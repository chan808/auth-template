package io.github.chan808.authtemplate.member.event

import io.github.chan808.authtemplate.common.mail.MailService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MemberEventListener(private val mailService: MailService) {

    // AFTER_COMMIT: 트랜잭션 롤백 시 메일 미발송 보장
    // mailService.sendVerificationEmail()이 @Async이므로 리스너 스레드는 즉시 반환
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMemberRegistered(event: MemberRegisteredEvent) {
        mailService.sendVerificationEmail(event.email, event.verificationToken)
    }
}
