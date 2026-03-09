package io.github.chan808.authtemplate.auth.service

import io.github.chan808.authtemplate.auth.repository.PasswordResetStore
import io.github.chan808.authtemplate.auth.repository.RefreshTokenStore
import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.mail.MailService
import io.github.chan808.authtemplate.common.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.repository.MemberRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PasswordResetService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val passwordResetStore: PasswordResetStore,
    private val mailService: MailService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val refreshTokenStore: RefreshTokenStore,
    private val passwordResetRateLimitService: PasswordResetRateLimitService,
) {
    private val log = LoggerFactory.getLogger(PasswordResetService::class.java)

    // 이메일 존재 여부와 무관하게 동일 응답 → enumeration attack 방지
    fun requestReset(email: String, ip: String) {
        val normalizedEmail = email.lowercase().trim()
        passwordResetRateLimitService.check(ip, normalizedEmail)

        val member = memberRepository.findByEmail(normalizedEmail) ?: return
        val token = UUID.randomUUID().toString()
        passwordResetStore.save(token, member.id)
        mailService.sendPasswordResetEmail(member.email, token)
        log.info("[AUTH] 비밀번호 재설정 이메일 발송 memberId={}", member.id)
    }

    @Transactional
    fun confirmReset(token: String, newPassword: String) {
        val memberId = passwordResetStore.findMemberId(token)
            ?: throw AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)
        val member = memberRepository.findById(memberId)
            .orElseThrow { AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID) }
        breachedPasswordChecker.check(newPassword, member.email)
        member.changePassword(passwordEncoder.encode(newPassword) ?: error("PasswordEncoder returned null"))
        passwordResetStore.delete(token)
        // 비밀번호 변경 후 모든 기존 세션 무효화 → 탈취된 세션 강제 로그아웃
        refreshTokenStore.deleteAllSessionsForMember(memberId)
        log.info("[AUTH] 비밀번호 재설정 완료 memberId={}", memberId)
    }
}
