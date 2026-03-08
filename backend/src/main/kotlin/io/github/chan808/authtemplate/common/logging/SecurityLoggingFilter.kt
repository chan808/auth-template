package io.github.chan808.authtemplate.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

// Spring Security 필터보다 먼저 실행되어 JWT 인증 로그에도 requestId, clientIp가 찍히도록 보장
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            // requestId: 한 요청에서 발생한 모든 로그를 묶어 공격 흐름 추적에 활용
            MDC.put("requestId", UUID.randomUUID().toString().take(8))
            MDC.put("clientIp", request.getHeader("X-Forwarded-For")?.split(",")?.first()?.trim() ?: request.remoteAddr)
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
