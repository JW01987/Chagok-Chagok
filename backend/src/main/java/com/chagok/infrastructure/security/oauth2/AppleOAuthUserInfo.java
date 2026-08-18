package com.chagok.infrastructure.security.oauth2;

import java.util.Map;

public class AppleOAuthUserInfo implements OAuthUserInfo {

	private static final String DEFAULT_NICKNAME = "애플 사용자";

	private final Map<String, Object> attributes;

	public AppleOAuthUserInfo(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	@Override
	public String getProviderId() {
		return String.valueOf(attributes.get("sub"));
	}

	@Override
	public String getEmail() {
		return (String) attributes.get("email");
	}

	@Override
	public String getNickname() {
		Object name = attributes.get("name");
		return (name instanceof String nickname && !nickname.isBlank()) ? nickname : DEFAULT_NICKNAME;
	}
}
