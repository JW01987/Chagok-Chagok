package com.chagok.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_oauth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOauth {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OAuthProvider provider;

	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;

	@Column(name = "access_token_encrypted", columnDefinition = "TEXT")
	private String accessTokenEncrypted;

	@Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
	private String refreshTokenEncrypted;

	@Column(name = "token_expires_at")
	private LocalDateTime tokenExpiresAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private UserOauth(User user, OAuthProvider provider, String providerUserId,
			String accessTokenEncrypted, LocalDateTime tokenExpiresAt) {
		this.user = user;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.accessTokenEncrypted = accessTokenEncrypted;
		this.tokenExpiresAt = tokenExpiresAt;
	}

	public static UserOauth of(User user, OAuthProvider provider, String providerUserId,
			String accessTokenEncrypted, LocalDateTime tokenExpiresAt) {
		return new UserOauth(user, provider, providerUserId, accessTokenEncrypted, tokenExpiresAt);
	}

	public void updateTokens(String accessTokenEncrypted, LocalDateTime tokenExpiresAt) {
		this.accessTokenEncrypted = accessTokenEncrypted;
		this.tokenExpiresAt = tokenExpiresAt;
	}
}
