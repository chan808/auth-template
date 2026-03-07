package io.github.chan808.authtemplate.member.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email(message = "올바른 이메일 형식을 입력해주세요.")
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.")
    // [ -~]: 출력 가능한 모든 ASCII(0x20-0x7E) 허용 — 특수문자 종류 제한 없음 (NIST SP 800-63B 권고)
    // [^A-Za-z0-9\s]: 공백을 특수문자로 카운트하지 않음
    @field:Pattern(
        regexp = """^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s])[ -~]+$""",
        message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.",
    )
    val password: String,
)
