package com.chagok.domain.auth;

import com.chagok.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "device_info")
	private String deviceInfo;

	@Column(name = "ip_address")
	private String ipAddress;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private RefreshToken(User user, String tokenHash, String ipAddress, String deviceInfo, LocalDateTime expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.ipAddress = ipAddress;
		this.deviceInfo = deviceInfo;
		this.expiresAt = expiresAt;
	}

	public static RefreshToken of(User user, String tokenHash, String ipAddress, String deviceInfo, LocalDateTime expiresAt) {
		return new RefreshToken(user, tokenHash, ipAddress, deviceInfo, expiresAt);
	}

	public boolean isRevoked() {
		return this.revokedAt != null;
	}

	public void revoke() {
		this.revokedAt = LocalDateTime.now();
	}
}
