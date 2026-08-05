package com.chagok.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

	private final SecretKey secretKey;
	private final long accessExpMs;
	private final long refreshExpMs;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-expiration-ms}") long accessExpMs,
			@Value("${jwt.refresh-expiration-ms}") long refreshExpMs) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessExpMs = accessExpMs;
		this.refreshExpMs = refreshExpMs;
	}

	public String generateAccessToken(Long userId, String email) {
		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim("email", email)
			.claim("type", "ACCESS")
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + accessExpMs))
			.signWith(secretKey)
			.compact();
	}

	public String generateRefreshToken(Long userId) {
		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim("type", "REFRESH")
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + refreshExpMs))
			.signWith(secretKey)
			.compact();
	}

	public Claims getClaims(String token) {
		return Jwts.parser()
			.verifyWith(secretKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public Long getUserId(String token) {
		return Long.parseLong(getClaims(token).getSubject());
	}

	public LocalDateTime getExpiration(String token) {
		return LocalDateTime.ofInstant(getClaims(token).getExpiration().toInstant(), ZoneId.systemDefault());
	}

	public boolean validateToken(String token) {
		try {
			getClaims(token);
			return true;
		} catch (ExpiredJwtException e) {
			log.debug("만료된 JWT 토큰: {}", e.getMessage());
		} catch (JwtException | IllegalArgumentException e) {
			log.debug("유효하지 않은 JWT 토큰: {}", e.getMessage());
		}
		return false;
	}
}
