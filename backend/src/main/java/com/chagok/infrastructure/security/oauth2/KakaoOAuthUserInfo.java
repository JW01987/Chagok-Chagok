package com.chagok.infrastructure.security.oauth2;

import java.util.Map;

public class KakaoOAuthUserInfo implements OAuthUserInfo {

	private final Map<String, Object> attributes;

	public KakaoOAuthUserInfo(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	@Override
	public String getProviderId() {
		return String.valueOf(attributes.get("id"));
	}

	@Override
	public String getEmail() {
		Map<String, Object> kakaoAccount = getKakaoAccount();
		return kakaoAccount == null ? null : (String) kakaoAccount.get("email");
	}

	@Override
	public String getNickname() {
		Map<String, Object> kakaoAccount = getKakaoAccount();
		if (kakaoAccount == null) {
			return null;
		}
		Map<String, Object> profile = getProfile(kakaoAccount);
		return profile == null ? null : (String) profile.get("nickname");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getKakaoAccount() {
		return (Map<String, Object>) attributes.get("kakao_account");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getProfile(Map<String, Object> kakaoAccount) {
		return (Map<String, Object>) kakaoAccount.get("profile");
	}
}
