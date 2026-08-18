package com.chagok.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserOauthTest {

	@Test
	@DisplayName("생성 시 provider와 providerUserId, 암호화된 토큰이 설정된다")
	void should_setFields_when_created() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();
		LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

		UserOauth userOauth = UserOauth.of(user, OAuthProvider.KAKAO, "12345", "encrypted-token", expiresAt);

		assertThat(userOauth.getUser()).isEqualTo(user);
		assertThat(userOauth.getProvider()).isEqualTo(OAuthProvider.KAKAO);
		assertThat(userOauth.getProviderUserId()).isEqualTo("12345");
		assertThat(userOauth.getAccessTokenEncrypted()).isEqualTo("encrypted-token");
		assertThat(userOauth.getTokenExpiresAt()).isEqualTo(expiresAt);
	}

	@Test
	@DisplayName("updateTokens 호출 시 암호화된 토큰과 만료시각이 갱신된다")
	void should_updateTokens_when_updateTokensCalled() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();
		UserOauth userOauth = UserOauth.of(user, OAuthProvider.KAKAO, "12345", "old-token",
			LocalDateTime.now().plusHours(1));
		LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(1);

		userOauth.updateTokens("new-token", newExpiresAt);

		assertThat(userOauth.getAccessTokenEncrypted()).isEqualTo("new-token");
		assertThat(userOauth.getTokenExpiresAt()).isEqualTo(newExpiresAt);
	}
}
