package io.github.chan808.authtemplate.member.api

import io.github.chan808.authtemplate.common.response.ApiResponse
import io.github.chan808.authtemplate.member.service.MemberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {

    @PostMapping
    fun signup(@RequestBody @Valid request: SignupRequest): ResponseEntity<ApiResponse<MemberResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(memberService.signup(request)))

    // principal = memberId(Long): JwtAuthenticationFilter에서 subject를 toLong()으로 설정
    @GetMapping("/me")
    fun getMyInfo(@AuthenticationPrincipal memberId: Long): ResponseEntity<ApiResponse<MemberResponse>> =
        ResponseEntity.ok(ApiResponse.of(memberService.getMyInfo(memberId)))
}
