package com.chagok.infrastructure.batch;

import com.chagok.domain.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

	private final RefreshTokenRepository refreshTokenRepository;

	@Scheduled(cron = "0 0 0 * * *")
	@Transactional
	public void cleanupExpiredTokens() {
		int deleted = refreshTokenRepository.deleteExpiredOrRevoked(LocalDateTime.now());
		log.info("만료/무효 Refresh Token 정리 완료: {}건", deleted);
	}
}
