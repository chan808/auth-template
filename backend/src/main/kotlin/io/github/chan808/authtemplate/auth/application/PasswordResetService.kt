package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.infrastructure.redis.PasswordResetStore
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.auth.application.port.AuthMailSender
import io.github.chan808.authtemplate.member.api.MemberApi
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PasswordResetService(
    private val memberApi: MemberApi,
    private val passwordResetStore: PasswordResetStore,
    private val mailSender: AuthMailSender,
    private val passwordResetRateLimitService: PasswordResetRateLimitService,
    @Value("\${app.base-url}") private val baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(PasswordResetService::class.java)

    // 이메일 존재 여부와 무관하게 동일 응답 → enumeration attack 방지
    fun requestReset(email: String, ip: String) {
        val normalizedEmail = email.lowercase().trim()
        passwordResetRateLimitService.check(ip, normalizedEmail)

        val member = memberApi.findAuthMemberByEmail(normalizedEmail) ?: return
        val token = UUID.randomUUID().toString()
        passwordResetStore.save(token, member.id)

        val resetLink = "$baseUrl/password-reset?token=$token"
        val body = """
            |비밀번호 재설정을 요청하셨습니다.
            |
            |아래 링크를 클릭하여 비밀번호를 재설정해주세요:
            |$resetLink
            |
            |이 링크는 30분간 유효합니다.
            |본인이 요청하지 않았다면 이 이메일을 무시해주세요.
        """.trimMargin()

        mailSender.send(member.email, "비밀번호 재설정", body)
        log.info("[AUTH] 비밀번호 재설정 이메일 발송 memberId={}", member.id)
    }

    fun confirmReset(token: String, newPassword: String) {
        val memberId = passwordResetStore.findMemberId(token)
            ?: throw AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)

        // member 모듈이 내부적으로 breached password 검증 + 인코딩을 처리
        memberApi.resetPassword(memberId, newPassword)
        passwordResetStore.delete(token)
        // 세션 무효화는 member가 발행하는 PasswordChangedEvent를 통해 AuthEventListener에서 처리
        log.info("[AUTH] 비밀번호 재설정 완료 memberId={}", memberId)
    }
}
