package com.chagok.infrastructure.batch;

import com.chagok.domain.auth.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

	@InjectMocks
	private TokenCleanupScheduler tokenCleanupScheduler;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	@DisplayName("만료/무효 토큰 정리 스케줄러가 repository 삭제를 호출한다")
	void should_deleteExpiredOrRevokedTokens_when_cleanupCalled() {
		given(refreshTokenRepository.deleteExpiredOrRevoked(any())).willReturn(3);

		tokenCleanupScheduler.cleanupExpiredTokens();

		verify(refreshTokenRepository).deleteExpiredOrRevoked(any());
	}
}
