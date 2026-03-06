package io.github.chan808.authtemplate.auth.service

import io.github.chan808.authtemplate.auth.api.LoginRequest
import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import io.github.chan808.authtemplate.auth.repository.RefreshTokenStore
import io.github.chan808.authtemplate.common.exception.AuthException
import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.security.JwtProvider
import io.github.chan808.authtemplate.member.repository.MemberRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class AuthService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val refreshTokenStore: RefreshTokenStore,
) {
    private val secureRandom = SecureRandom()

    fun login(request: LoginRequest): Pair<String, String> {
        val email = request.email.lowercase().trim()
        val member = memberRepository.findByEmail(email)
            ?: throw AuthException(ErrorCode.INVALID_CREDENTIALS)

        // 이메일 존재 여부와 비밀번호 오류를 같은 예외로 처리 → 계정 열거 공격(enumeration attack) 방지
        if (!passwordEncoder.matches(request.password, member.password)) {
            throw AuthException(ErrorCode.INVALID_CREDENTIALS)
        }

        val (sid, rt) = generateRefreshToken()
        refreshTokenStore.save(
            sid = sid,
            session = RefreshTokenSession(
                memberId = member.id,
                role = member.role.name,
                tokenHash = hashToken(rt),
                absoluteExpiryEpoch = Instant.now().plusSeconds(30L * 24 * 3600).epochSecond,
            ),
            ttlSeconds = 7L * 24 * 3600, // sliding window: 재발급 시마다 TTL 갱신
        )

        return jwtProvider.generateAccessToken(member.id, member.role.name) to rt
    }

    fun reissue(rtToken: String): Pair<String, String> {
        val sid = parseSid(rtToken)

        if (!refreshTokenStore.tryLock(sid)) {
            throw AuthException(ErrorCode.REISSUE_CONFLICT)
        }

        try {
            val session = refreshTokenStore.find(sid)
                ?: throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)

            // MessageDigest.isEqual: 상수시간 비교로 timing attack 방지
            if (!MessageDigest.isEqual(
                    session.tokenHash.toByteArray(Charsets.UTF_8),
                    hashToken(rtToken).toByteArray(Charsets.UTF_8),
                )
            ) {
                // 해시 불일치: RT 탈취 후 재사용 가능성 → 세션 즉시 무효화 (토큰 로테이션 보안)
                refreshTokenStore.delete(sid)
                throw AuthException(ErrorCode.REFRESH_TOKEN_MISMATCH)
            }

            if (session.absoluteExpiryEpoch < Instant.now().epochSecond) {
                refreshTokenStore.delete(sid)
                throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
            }

            // sid 유지 + random part 교체: 세션 연속성 유지하면서 RT 단방향 로테이션
            val newRt = "$sid.${generateRandomPart()}"
            refreshTokenStore.save(sid, session.copy(tokenHash = hashToken(newRt)), 7L * 24 * 3600)

            return jwtProvider.generateAccessToken(session.memberId, session.role) to newRt
        } finally {
            refreshTokenStore.releaseLock(sid)
        }
    }

    fun logout(rtToken: String?) {
        rtToken ?: return
        refreshTokenStore.delete(parseSid(rtToken))
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
