package io.github.chan808.authtemplate.common.exception

import org.springframework.http.HttpStatus

// HTTP 상태와 메시지를 한 곳에서 관리해 클라이언트 계약을 명확히 하고 코드 산발 방지
enum class ErrorCode(
    val httpStatus: HttpStatus,
    val message: String,
) {
    // auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다. 메일함을 확인해주세요."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증된 이메일입니다."),
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 인증 토큰입니다."),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 비밀번호 재설정 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 일치하지 않습니다."),
    REISSUE_CONFLICT(HttpStatus.CONFLICT, "토큰 재발급이 진행 중입니다. 잠시 후 재시도해주세요."), // Redis SETNX 락 충돌
    OAUTH_ACCOUNT_NO_PASSWORD(HttpStatus.BAD_REQUEST, "소셜 로그인으로 가입된 계정입니다. 해당 소셜 로그인을 이용해주세요."),
    OAUTH_CODE_NOT_FOUND(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 코드입니다."),

    // member
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    BREACHED_PASSWORD(HttpStatus.BAD_REQUEST, "이미 유출된 비밀번호입니다. 다른 비밀번호를 사용해주세요."),

    // common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
}
