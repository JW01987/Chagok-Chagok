package com.chagok.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

	@Test
	@DisplayName("로그인 실패 횟수가 5회 미만이면 잠기지 않는다")
	void should_notLock_when_loginFailCountBelowThreshold() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();

		for (int i = 0; i < 4; i++) {
			user.increaseLoginFailCount();
		}

		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getLoginFailCount()).isEqualTo((short) 4);
	}

	@Test
	@DisplayName("로그인 실패 횟수가 5회가 되면 15분간 계정이 잠긴다")
	void should_lockForFifteenMinutes_when_loginFailCountReachesFive() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();

		for (int i = 0; i < 5; i++) {
			user.increaseLoginFailCount();
		}

		assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
		assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
	}

	@Test
	@DisplayName("로그인 성공 시 실패 횟수가 초기화된다")
	void should_resetFailCount_when_loginSucceeds() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).loginFailCount(3).build();

		user.resetLoginFailCount();

		assertThat(user.getLoginFailCount()).isEqualTo((short) 0);
	}

	@Test
	@DisplayName("잠금 시간이 지나지 않았으면 isLocked는 true를 반환한다")
	void should_returnTrue_when_lockNotExpired() {
		User user = User.builder().email("test@test.com").status(UserStatus.LOCKED).loginFailCount(5).build();
		user.increaseLoginFailCount();

		assertThat(user.isLocked()).isTrue();
	}

	@Test
	@DisplayName("잠금 시간이 지났으면 자동으로 잠금이 해제된다")
	void should_autoUnlockAndResetStatus_when_lockExpired() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();
		for (int i = 0; i < 5; i++) {
			user.increaseLoginFailCount();
		}
		setLockedUntilToPast(user);

		boolean locked = user.isLocked();

		assertThat(locked).isFalse();
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getLoginFailCount()).isEqualTo((short) 0);
	}

	@Test
	@DisplayName("ACTIVE 상태면 isLocked는 false를 반환한다")
	void should_returnFalse_when_statusIsActive() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();

		assertThat(user.isLocked()).isFalse();
	}

	@Test
	@DisplayName("로그인 성공 시 마지막 로그인 시각이 갱신된다")
	void should_updateLastLoginAt_when_called() {
		User user = User.builder().email("test@test.com").status(UserStatus.ACTIVE).build();

		user.updateLastLoginAt();

		assertThat(user.getLastLoginAt()).isNotNull();
		assertThat(user.getLastLoginAt()).isBeforeOrEqualTo(LocalDateTime.now());
	}

	private void setLockedUntilToPast(User user) {
		try {
			var field = User.class.getDeclaredField("lockedUntil");
			field.setAccessible(true);
			field.set(user, LocalDateTime.now().minusMinutes(1));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
