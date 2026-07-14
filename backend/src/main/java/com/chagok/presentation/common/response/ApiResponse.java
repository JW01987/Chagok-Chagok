package com.chagok.presentation.common.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

	private final boolean success;
	private final T data;
	private final ErrorResponse error;

	private ApiResponse(boolean success, T data, ErrorResponse error) {
		this.success = success;
		this.data = data;
		this.error = error;
	}

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> fail(ErrorResponse error) {
		return new ApiResponse<>(false, null, error);
	}
}
