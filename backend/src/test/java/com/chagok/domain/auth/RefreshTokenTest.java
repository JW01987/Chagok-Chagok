package com.chagok.domain.auth;

import com.chagok.domain.user.User;
import com.chagok.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

	@Test
	@DisplayName("생성 직후에는 무효화되지 않은 상태다")
	void should_notBeRevoked_when_justCreated() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();
		RefreshToken token = RefreshToken.of(user, "hash", "127.0.0.1", "agent", LocalDateTime.now().plusDays(30));

		assertThat(token.isRevoked()).isFalse();
	}

	@Test
	@DisplayName("revoke 호출 시 무효화 시각이 기록된다")
	void should_recordRevokedAt_when_revokeCalled() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();
		RefreshToken token = RefreshToken.of(user, "hash", null, null, LocalDateTime.now().plusDays(30));

		token.revoke();

		assertThat(token.isRevoked()).isTrue();
		assertThat(token.getRevokedAt()).isNotNull();
	}
}
