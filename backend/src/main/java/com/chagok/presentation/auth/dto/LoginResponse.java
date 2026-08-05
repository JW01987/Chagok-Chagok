package com.chagok.presentation.auth.dto;

import lombok.Getter;

@Getter
public class LoginResponse {

	private final String accessToken;
	private final String refreshToken;
	private final Long userId;

	public LoginResponse(String accessToken, String refreshToken, Long userId) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.userId = userId;
	}
}
