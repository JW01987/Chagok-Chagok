package com.chagok.infrastructure.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppleOAuthUserInfoTest {

	@Test
	@DisplayName("애플 응답에서 sub, 이메일, 이름을 추출한다")
	void should_extractSubEmailAndName_when_attributesComplete() {
		Map<String, Object> attributes = Map.of(
			"sub", "apple-user-id",
			"email", "apple@test.com",
			"name", "홍길동"
		);

		AppleOAuthUserInfo userInfo = new AppleOAuthUserInfo(attributes);

		assertThat(userInfo.getProviderId()).isEqualTo("apple-user-id");
		assertThat(userInfo.getEmail()).isEqualTo("apple@test.com");
		assertThat(userInfo.getNickname()).isEqualTo("홍길동");
	}

	@Test
	@DisplayName("최초 로그인 이후에는 이름이 제공되지 않아 기본 닉네임을 사용한다")
	void should_useDefaultNickname_when_nameNotProvided() {
		Map<String, Object> attributes = Map.of(
			"sub", "apple-user-id",
			"email", "apple@test.com"
		);

		AppleOAuthUserInfo userInfo = new AppleOAuthUserInfo(attributes);

		assertThat(userInfo.getNickname()).isEqualTo("애플 사용자");
	}
}
