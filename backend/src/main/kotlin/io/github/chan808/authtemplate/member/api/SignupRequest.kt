package io.github.chan808.authtemplate.member.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email(message = "올바른 이메일 형식을 입력해주세요.")
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 50, message = "비밀번호는 8자 이상 50자 이하여야 합니다.")
    val password: String,
)
