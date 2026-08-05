package com.chagok.presentation.auth.dto;

import lombok.Getter;

@Getter
public class SignupResponse {

	private final Long userId;
	private final String email;
	private final String nickname;

	public SignupResponse(Long userId, String email, String nickname) {
		this.userId = userId;
		this.email = email;
		this.nickname = nickname;
	}
}
