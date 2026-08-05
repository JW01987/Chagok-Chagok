package com.chagok.domain.subscription;

import com.chagok.domain.user.User;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subscription_plan_id", nullable = false)
	private SubscriptionPlan subscriptionPlan;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserSubscriptionStatus status;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "payment_method")
	private String paymentMethod;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private UserSubscription(User user, SubscriptionPlan subscriptionPlan, UserSubscriptionStatus status, LocalDateTime startedAt) {
		this.user = user;
		this.subscriptionPlan = subscriptionPlan;
		this.status = status;
		this.startedAt = startedAt;
	}

	public static UserSubscription freeOf(User user, SubscriptionPlan freePlan) {
		return new UserSubscription(user, freePlan, UserSubscriptionStatus.ACTIVE, LocalDateTime.now());
	}
}
