package com.chagok.presentation.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "서버 내부 오류가 발생했습니다."),

	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_001", "중복된 이메일입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_002", "존재하지 않는 사용자입니다."),
	ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH_003", "로그인 실패 횟수를 초과하여 계정이 잠겼습니다."),
	INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_004", "비밀번호가 일치하지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_005", "유효하지 않은 리프레시 토큰입니다."),
	REVOKED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_006", "이미 사용되었거나 무효화된 리프레시 토큰입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	ErrorCode(HttpStatus httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}
}
