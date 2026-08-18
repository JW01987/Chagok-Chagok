package com.chagok.infrastructure.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

public final class OAuthUserInfoFactory {

	private OAuthUserInfoFactory() {
	}

	public static OAuthUserInfo getOAuthUserInfo(String provider, Map<String, Object> attributes) {
		return switch (provider) {
			case "KAKAO" -> new KakaoOAuthUserInfo(attributes);
			case "APPLE" -> new AppleOAuthUserInfo(attributes);
			default -> throw new OAuth2AuthenticationException(
				new OAuth2Error("unsupported_provider", "지원하지 않는 소셜 로그인 제공자입니다: " + provider, null));
		};
	}
}
