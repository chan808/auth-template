package io.github.chan808.authtemplate.member.api

import io.github.chan808.authtemplate.common.response.ApiResponse
import io.github.chan808.authtemplate.member.service.MemberService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {

    @PostMapping
    fun signup(
        @RequestBody @Valid request: SignupRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<MemberResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(memberService.signup(request, servletRequest.clientIp())))

    // principal = memberId(Long): JwtAuthenticationFilter에서 subject를 toLong()으로 설정
    @GetMapping("/me")
    fun getMyInfo(@AuthenticationPrincipal memberId: Long): ResponseEntity<ApiResponse<MemberResponse>> =
        ResponseEntity.ok(ApiResponse.of(memberService.getMyInfo(memberId)))

    @PatchMapping("/me/password")
    fun changePassword(
        @RequestBody @Valid request: ChangePasswordRequest,
        @AuthenticationPrincipal memberId: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        memberService.changePassword(memberId, request)
        return ResponseEntity.ok(ApiResponse.success())
    }
}

// X-Forwarded-For: 역방향 프록시(nginx 등) 뒤에서 실제 클라이언트 IP 추출
// 운영 환경에서는 nginx가 신뢰된 헤더만 전달하도록 설정 필요 (헤더 스푸핑 방지)
private fun HttpServletRequest.clientIp(): String =
    getHeader("X-Forwarded-For")?.split(",")?.first()?.trim() ?: remoteAddr
