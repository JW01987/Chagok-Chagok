package com.chagok.presentation.common.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

	@Test
	void should_wrapDataWithSuccessTrue_when_okCalled() {
		ApiResponse<String> response = ApiResponse.ok("data");

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getData()).isEqualTo("data");
		assertThat(response.getError()).isNull();
	}

	@Test
	void should_wrapErrorWithSuccessFalse_when_failCalled() {
		ErrorResponse error = new ErrorResponse("COMMON_001", "요청 값이 올바르지 않습니다.");

		ApiResponse<Void> response = ApiResponse.fail(error);

		assertThat(response.isSuccess()).isFalse();
		assertThat(response.getData()).isNull();
		assertThat(response.getError()).isEqualTo(error);
	}
}
