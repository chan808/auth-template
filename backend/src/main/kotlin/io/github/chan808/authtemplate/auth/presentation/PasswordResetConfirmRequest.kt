package io.github.chan808.authtemplate.auth.presentation

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class PasswordResetConfirmRequest(
    @field:NotBlank
    val token: String,

    @field:NotBlank
    @field:Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.")
    @field:Pattern(
        regexp = """^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s])[ -~]+$""",
        message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.",
    )
    val newPassword: String,
)
