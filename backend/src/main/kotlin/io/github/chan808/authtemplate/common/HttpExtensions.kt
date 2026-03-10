package io.github.chan808.authtemplate.common

import jakarta.servlet.http.HttpServletRequest

// X-Forwarded-For: 역방향 프록시(nginx 등) 뒤에서 실제 클라이언트 IP 추출
// 운영 환경에서는 nginx가 신뢰된 헤더만 전달하도록 설정 필요 (헤더 스푸핑 방지)
fun HttpServletRequest.clientIp(): String =
    getHeader("X-Forwarded-For")?.split(",")?.first()?.trim() ?: remoteAddr
