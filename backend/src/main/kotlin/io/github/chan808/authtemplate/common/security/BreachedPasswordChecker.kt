package io.github.chan808.authtemplate.common.security

import io.github.chan808.authtemplate.common.exception.ErrorCode
import io.github.chan808.authtemplate.common.exception.MemberException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.security.MessageDigest

@Component
class BreachedPasswordChecker(
    @Value("\${app.name:auth-template}") private val serviceName: String,
) {
    private val restClient = RestClient.create()
    private val log = LoggerFactory.getLogger(BreachedPasswordChecker::class.java)

    fun check(password: String, email: String? = null) {
        // NIST 5.1.1.2: context-specific words 먼저 체크 (로컬, 빠름)
        checkContextSpecific(password, email)
        // NIST 5.1.1.2: 알려진 유출 비밀번호 체크 (HIBP k-anonymity API)
        checkHibp(password)
    }

    private fun checkContextSpecific(password: String, email: String?) {
        val lower = password.lowercase()

        if (lower.contains(serviceName.lowercase())) {
            throw MemberException(ErrorCode.BREACHED_PASSWORD, "비밀번호에 서비스 이름을 포함할 수 없습니다.")
        }

        if (email != null) {
            // 이메일 로컬 파트(@앞): 3자 미만은 너무 짧아 오탐 가능성이 높으므로 제외
            val localPart = email.substringBefore('@').lowercase()
            if (localPart.length >= 3 && lower.contains(localPart)) {
                throw MemberException(ErrorCode.BREACHED_PASSWORD, "비밀번호에 이메일 주소를 포함할 수 없습니다.")
            }
        }
    }

    private fun checkHibp(password: String) {
        val sha1 = sha1Hex(password).uppercase()
        val prefix = sha1.take(5)
        val suffix = sha1.drop(5)

        try {
            val body = restClient.get()
                .uri("https://api.pwnedpasswords.com/range/$prefix")
                // Add-Padding: 응답 크기를 균일하게 해 트래픽 분석으로 해시 유추 방지
                .header("Add-Padding", "true")
                .retrieve()
                .body(String::class.java) ?: return

            if (body.lines().any { it.substringBefore(':').equals(suffix, ignoreCase = true) }) {
                throw MemberException(ErrorCode.BREACHED_PASSWORD)
            }
        } catch (ex: MemberException) {
            throw ex
        } catch (ex: Exception) {
            // fail-open: HIBP API 장애 시 서비스 중단보다 미체크가 위험도 낮음
            log.warn("HIBP API 호출 실패, 유출 비밀번호 체크 생략: {}", ex.message)
        }
    }

    // k-anonymity: SHA-1 앞 5자리만 HIBP에 전송 → 비밀번호 원문·전체 해시 외부 미노출
    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
