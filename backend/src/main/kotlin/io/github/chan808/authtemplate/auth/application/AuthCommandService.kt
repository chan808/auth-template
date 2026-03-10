package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.api.AuthApi
import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProvider
import io.github.chan808.authtemplate.auth.presentation.LoginRequest
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.maskEmail
import io.github.chan808.authtemplate.member.api.MemberApi
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class AuthCommandService(
    private val memberApi: MemberApi,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val tokenStore: TokenStore,
    private val loginRateLimitService: LoginRateLimitService,
) : AuthApi {

    private val log = LoggerFactory.getLogger(AuthCommandService::class.java)
    private val secureRandom = SecureRandom()

    fun login(request: LoginRequest, ip: String): Pair<String, String> {
        val email = request.email.lowercase().trim()
        loginRateLimitService.check(ip, email)

        val member = memberApi.findAuthMemberByEmail(email) ?: run {
            log.warn("[AUTH] 인증 실패 email={} reason=EMAIL_NOT_FOUND", maskEmail(email))
            throw AuthException(ErrorCode.INVALID_CREDENTIALS)
        }

        // 소셜 로그인으로 가입한 계정은 비밀번호 로그인 불가
        if (member.isOAuthAccount) {
            log.warn("[AUTH] 인증 실패 email={} reason=OAUTH_ACCOUNT provider={}", maskEmail(email), member.provider)
            throw AuthException(ErrorCode.OAUTH_ACCOUNT_NO_PASSWORD)
        }

        // 이메일 존재 여부와 비밀번호 오류를 같은 예외로 처리 → 계정 열거 공격(enumeration attack) 방지
        if (!passwordEncoder.matches(request.password, member.encodedPassword)) {
            log.warn("[AUTH] 인증 실패 email={} reason=INVALID_PASSWORD", maskEmail(email))
            throw AuthException(ErrorCode.INVALID_CREDENTIALS)
        }

        // NIST SP 800-63B 6.1.2: 이메일 인증 전 로그인 차단
        if (!member.emailVerified) {
            log.warn("[AUTH] 인증 실패 email={} reason=EMAIL_NOT_VERIFIED", maskEmail(email))
            throw AuthException(ErrorCode.EMAIL_NOT_VERIFIED)
        }

        val (sid, rt) = generateRefreshToken()
        tokenStore.save(
            sid = sid,
            session = RefreshTokenSession(
                memberId = member.id,
                role = member.role.name,
                tokenHash = hashToken(rt),
                absoluteExpiryEpoch = Instant.now().plusSeconds(30L * 24 * 3600).epochSecond,
            ),
            ttlSeconds = 7L * 24 * 3600, // sliding window: 재발급 시마다 TTL 갱신
        )
        // 비밀번호 변경/재설정 시 전체 세션 일괄 무효화를 위해 회원별 세션 세트에 등록
        tokenStore.addSession(member.id, sid)

        return jwtProvider.generateAccessToken(member.id, member.role.name) to rt
    }

    fun reissue(rtToken: String): Pair<String, String> {
        val sid = parseSid(rtToken)

        if (!tokenStore.tryLock(sid)) {
            throw AuthException(ErrorCode.REISSUE_CONFLICT)
        }

        try {
            val session = tokenStore.find(sid)
                ?: throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)

            // MessageDigest.isEqual: 상수시간 비교로 timing attack 방지
            if (!MessageDigest.isEqual(
                    session.tokenHash.toByteArray(Charsets.UTF_8),
                    hashToken(rtToken).toByteArray(Charsets.UTF_8),
                )
            ) {
                // 해시 불일치: RT 탈취 후 재사용 가능성 → 세션 즉시 무효화 (토큰 로테이션 보안)
                tokenStore.delete(sid)
                log.error("[SECURITY] RT 해시 불일치 감지 - 토큰 탈취 의심 sid={}", sid)
                throw AuthException(ErrorCode.REFRESH_TOKEN_MISMATCH)
            }

            if (session.absoluteExpiryEpoch < Instant.now().epochSecond) {
                tokenStore.delete(sid)
                throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
            }

            // sid 유지 + random part 교체: 세션 연속성 유지하면서 RT 단방향 로테이션
            val newRt = "$sid.${generateRandomPart()}"
            tokenStore.save(sid, session.copy(tokenHash = hashToken(newRt)), 7L * 24 * 3600)

            return jwtProvider.generateAccessToken(session.memberId, session.role) to newRt
        } finally {
            tokenStore.releaseLock(sid)
        }
    }

    fun logout(rtToken: String?) {
        rtToken ?: return
        tokenStore.delete(parseSid(rtToken))
    }

    /** OAuth2SuccessHandler에서 RT/AT 발급 시 사용 */
    fun issueTokensForOAuth(memberId: Long, role: String = "USER"): Pair<String, String> {
        val (sid, rt) = generateRefreshToken()
        tokenStore.save(
            sid = sid,
            session = RefreshTokenSession(
                memberId = memberId,
                role = role,
                tokenHash = hashToken(rt),
                absoluteExpiryEpoch = Instant.now().plusSeconds(30L * 24 * 3600).epochSecond,
            ),
            ttlSeconds = 7L * 24 * 3600,
        )
        tokenStore.addSession(memberId, sid)
        return jwtProvider.generateAccessToken(memberId, role) to rt
    }

    override fun invalidateAllSessions(memberId: Long) {
        tokenStore.deleteAllSessionsForMember(memberId)
    }

    private fun generateRefreshToken(): Pair<String, String> {
        val sid = UUID.randomUUID().toString()
        return sid to "$sid.${generateRandomPart()}"
    }

    private fun generateRandomPart(): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // dot이 없거나 맨 앞에 위치하면 유효하지 않은 RT 형식 — substringBefore만으로는 dot 부재 시 전체 문자열 반환
    private fun parseSid(token: String): String {
        val dotIndex = token.indexOf('.')
        if (dotIndex <= 0) throw AuthException(ErrorCode.TOKEN_INVALID)
        return token.substring(0, dotIndex)
    }

    // SHA-256: RT 원문 대신 해시만 Redis에 저장 → Redis 유출 시 RT 재사용 불가
    private fun hashToken(token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(bytes)
    }
}
