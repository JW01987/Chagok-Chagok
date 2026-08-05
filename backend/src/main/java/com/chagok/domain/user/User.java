package com.chagok.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	private static final int MAX_LOGIN_FAIL_COUNT = 5;
	private static final long LOCK_DURATION_MINUTES = 15;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash")
	private String passwordHash;

	@Column(nullable = false, length = 50)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "login_fail_count", nullable = false)
	private short loginFailCount;

	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Builder
	public User(String email, String passwordHash, String nickname, UserStatus status, int loginFailCount) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
		this.status = status;
		this.loginFailCount = (short) loginFailCount;
	}

	public void increaseLoginFailCount() {
		this.loginFailCount++;
		if (this.loginFailCount >= MAX_LOGIN_FAIL_COUNT) {
			this.status = UserStatus.LOCKED;
			this.lockedUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
		}
	}

	public void resetLoginFailCount() {
		this.loginFailCount = 0;
	}

	public void updateLastLoginAt() {
		this.lastLoginAt = LocalDateTime.now();
	}

	public boolean isLocked() {
		if (this.status != UserStatus.LOCKED) {
			return false;
		}
		if (this.lockedUntil != null && LocalDateTime.now().isAfter(this.lockedUntil)) {
			this.status = UserStatus.ACTIVE;
			this.loginFailCount = 0;
			return false;
		}
		return true;
	}
}
