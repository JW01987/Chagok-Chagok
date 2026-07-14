package com.chagok.presentation.common.exception;

import com.chagok.presentation.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Mock
	private MethodArgumentNotValidException methodArgumentNotValidException;

	@Mock
	private BindingResult bindingResult;

	@Test
	void should_return400WithErrorCode_when_businessExceptionThrown() {
		BusinessException exception = new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

		ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().isSuccess()).isFalse();
		assertThat(response.getBody().getError().getCode()).isEqualTo("COMMON_001");
	}

	@Test
	void should_return400WithFieldErrorMessage_when_validationFails() {
		FieldError fieldError = new FieldError("target", "field", "필드는 비어있을 수 없습니다.");
		when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

		ResponseEntity<ApiResponse<Void>> response =
			handler.handleMethodArgumentNotValidException(methodArgumentNotValidException);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getError().getMessage()).isEqualTo("필드는 비어있을 수 없습니다.");
	}

	@Test
	void should_return500WithoutStackTrace_when_unexpectedExceptionThrown() {
		ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException("boom"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().getError().getCode()).isEqualTo("COMMON_002");
		assertThat(response.getBody().getError().getMessage()).isEqualTo("서버 내부 오류가 발생했습니다.");
	}
}
