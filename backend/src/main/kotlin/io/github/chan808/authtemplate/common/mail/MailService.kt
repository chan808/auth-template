package io.github.chan808.authtemplate.common.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class MailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
    @Value("\${app.base-url}") private val baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(MailService::class.java)

    @Async
    fun sendVerificationEmail(to: String, token: String) {
        val link = "$baseUrl/verify-email?token=$token"
        runCatching {
            mailSender.send(mailSender.createMimeMessage().also { msg ->
                MimeMessageHelper(msg, false, "UTF-8").apply {
                    setFrom(from)
                    setTo(to)
                    setSubject("[Auth Template] 이메일 인증")
                    setText("아래 링크를 클릭해 이메일을 인증해주세요. (24시간 유효)\n\n$link")
                }
            })
        }.onFailure { log.error("이메일 인증 메일 발송 실패 [to={}]: {}", to, it.message) }
    }

    @Async
    fun sendPasswordResetEmail(to: String, token: String) {
        val link = "$baseUrl/reset-password?token=$token"
        runCatching {
            mailSender.send(mailSender.createMimeMessage().also { msg ->
                MimeMessageHelper(msg, false, "UTF-8").apply {
                    setFrom(from)
                    setTo(to)
                    setSubject("[Auth Template] 비밀번호 재설정")
                    setText("아래 링크를 클릭해 비밀번호를 재설정해주세요. (30분 이내 유효)\n\n$link")
                }
            })
        }.onFailure { log.error("비밀번호 재설정 메일 발송 실패 [to={}]: {}", to, it.message) }
    }
}
