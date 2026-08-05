package com.chagok.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

	private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-string";

	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(SECRET, 900_000L, 2_592_000_000L);
	}

	@Test
	@DisplayName("Access Token 생성 시 subject와 email claim이 포함된다")
	void should_containUserIdAndEmail_when_accessTokenGenerated() {
		String token = jwtTokenProvider.generateAccessToken(1L, "test@test.com");

		assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(1L);
		assertThat(jwtTokenProvider.getClaims(token).get("email")).isEqualTo("test@test.com");
		assertThat(jwtTokenProvider.getClaims(token).get("type")).isEqualTo("ACCESS");
	}

	@Test
	@DisplayName("Refresh Token 생성 시 subject가 포함된다")
	void should_containUserId_when_refreshTokenGenerated() {
		String token = jwtTokenProvider.generateRefreshToken(1L);

		assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(1L);
		assertThat(jwtTokenProvider.getClaims(token).get("type")).isEqualTo("REFRESH");
	}

	@Test
	@DisplayName("정상 토큰은 유효성 검증을 통과한다")
	void should_returnTrue_when_tokenIsValid() {
		String token = jwtTokenProvider.generateAccessToken(1L, "test@test.com");

		assertThat(jwtTokenProvider.validateToken(token)).isTrue();
	}

	@Test
	@DisplayName("만료된 토큰은 유효성 검증에 실패한다")
	void should_returnFalse_when_tokenExpired() {
		JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1_000L, -1_000L);
		String token = expiredProvider.generateAccessToken(1L, "test@test.com");

		assertThat(expiredProvider.validateToken(token)).isFalse();
	}

	@Test
	@DisplayName("형식이 올바르지 않은 토큰은 유효성 검증에 실패한다")
	void should_returnFalse_when_tokenMalformed() {
		assertThat(jwtTokenProvider.validateToken("not-a-jwt")).isFalse();
	}

	@Test
	@DisplayName("서명이 다른 토큰은 유효성 검증에 실패한다")
	void should_returnFalse_when_tokenSignedWithDifferentKey() {
		JwtTokenProvider otherProvider = new JwtTokenProvider(
			"other-secret-key-must-be-at-least-256-bits-long-string", 900_000L, 2_592_000_000L);
		String token = otherProvider.generateAccessToken(1L, "test@test.com");

		assertThat(jwtTokenProvider.validateToken(token)).isFalse();
	}

	@Test
	@DisplayName("토큰의 만료 시각을 LocalDateTime으로 변환할 수 있다")
	void should_returnFutureExpiration_when_getExpirationCalled() {
		String token = jwtTokenProvider.generateAccessToken(1L, "test@test.com");

		LocalDateTime expiration = jwtTokenProvider.getExpiration(token);

		assertThat(expiration).isAfter(LocalDateTime.now());
	}
}
