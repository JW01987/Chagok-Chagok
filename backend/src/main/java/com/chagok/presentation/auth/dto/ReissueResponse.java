package com.chagok.presentation.auth.dto;

import lombok.Getter;

@Getter
public class ReissueResponse {

	private final String accessToken;
	private final String refreshToken;

	public ReissueResponse(String accessToken, String refreshToken) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
	}
}
