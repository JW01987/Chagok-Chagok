package com.chagok.infrastructure.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthUserInfoTest {

	@Test
	@DisplayName("카카오 응답에서 id, 이메일, 닉네임을 추출한다")
	void should_extractIdEmailAndNickname_when_attributesComplete() {
		Map<String, Object> attributes = Map.of(
			"id", 123456789L,
			"kakao_account", Map.of(
				"email", "kakao@test.com",
				"profile", Map.of("nickname", "카카오유저")
			)
		);

		KakaoOAuthUserInfo userInfo = new KakaoOAuthUserInfo(attributes);

		assertThat(userInfo.getProviderId()).isEqualTo("123456789");
		assertThat(userInfo.getEmail()).isEqualTo("kakao@test.com");
		assertThat(userInfo.getNickname()).isEqualTo("카카오유저");
	}

	@Test
	@DisplayName("kakao_account가 없으면 이메일과 닉네임은 null이다")
	void should_returnNull_when_kakaoAccountMissing() {
		Map<String, Object> attributes = Map.of("id", 1L);

		KakaoOAuthUserInfo userInfo = new KakaoOAuthUserInfo(attributes);

		assertThat(userInfo.getEmail()).isNull();
		assertThat(userInfo.getNickname()).isNull();
	}

	@Test
	@DisplayName("동의항목에서 이메일 제공을 거부하면 이메일은 null이다")
	void should_returnNullEmail_when_emailConsentDenied() {
		Map<String, Object> attributes = Map.of(
			"id", 1L,
			"kakao_account", Map.of("profile", Map.of("nickname", "카카오유저"))
		);

		KakaoOAuthUserInfo userInfo = new KakaoOAuthUserInfo(attributes);

		assertThat(userInfo.getEmail()).isNull();
		assertThat(userInfo.getNickname()).isEqualTo("카카오유저");
	}
}
