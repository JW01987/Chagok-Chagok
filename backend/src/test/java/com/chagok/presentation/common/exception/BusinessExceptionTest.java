package com.chagok.presentation.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

	@Test
	void should_carryErrorCodeAndMessage_when_constructedWithErrorCode() {
		BusinessException exception = new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		assertThat(exception.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
		assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exception.getErrorCode().getCode()).isEqualTo("COMMON_001");
	}
}
