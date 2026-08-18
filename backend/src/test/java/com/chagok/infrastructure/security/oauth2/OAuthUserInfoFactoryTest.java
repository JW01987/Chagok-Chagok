package com.chagok.infrastructure.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthUserInfoFactoryTest {

	@Test
	@DisplayName("provider가 KAKAO면 KakaoOAuthUserInfo를 반환한다")
	void should_returnKakaoOAuthUserInfo_when_providerIsKakao() {
		OAuthUserInfo userInfo = OAuthUserInfoFactory.getOAuthUserInfo("KAKAO", Map.of("id", 1L));

		assertThat(userInfo).isInstanceOf(KakaoOAuthUserInfo.class);
	}

	@Test
	@DisplayName("provider가 APPLE이면 AppleOAuthUserInfo를 반환한다")
	void should_returnAppleOAuthUserInfo_when_providerIsApple() {
		OAuthUserInfo userInfo = OAuthUserInfoFactory.getOAuthUserInfo("APPLE", Map.of("sub", "id"));

		assertThat(userInfo).isInstanceOf(AppleOAuthUserInfo.class);
	}

	@Test
	@DisplayName("지원하지 않는 provider면 OAuth2AuthenticationException이 발생한다")
	void should_throwOAuth2AuthenticationException_when_providerUnsupported() {
		assertThatThrownBy(() -> OAuthUserInfoFactory.getOAuthUserInfo("NAVER", Map.of()))
			.isInstanceOf(OAuth2AuthenticationException.class);
	}
}
