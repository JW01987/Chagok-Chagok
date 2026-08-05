package com.chagok.domain.notification;

import com.chagok.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "savings_reminder", nullable = false)
	private boolean savingsReminder = true;

	@Column(name = "savings_reminder_days_before", nullable = false)
	private short savingsReminderDaysBefore = 3;

	@Column(name = "savings_completed", nullable = false)
	private boolean savingsCompleted = true;

	@Column(name = "insufficient_balance", nullable = false)
	private boolean insufficientBalance = true;

	@Column(name = "weekly_report", nullable = false)
	private boolean weeklyReport = true;

	@Column(name = "rebalancing_alert", nullable = false)
	private boolean rebalancingAlert = true;

	@Column(name = "goal_milestone", nullable = false)
	private boolean goalMilestone = true;

	@Column(name = "market_alert", nullable = false)
	private boolean marketAlert = false;

	@Column(name = "channel_push", nullable = false)
	private boolean channelPush = true;

	@Column(name = "channel_kakao", nullable = false)
	private boolean channelKakao = false;

	@Column(name = "channel_email", nullable = false)
	private boolean channelEmail = false;

	@Column(name = "quiet_start_time")
	private LocalTime quietStartTime;

	@Column(name = "quiet_end_time")
	private LocalTime quietEndTime;

	@Column(name = "fcm_token")
	private String fcmToken;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private NotificationSettings(User user) {
		this.user = user;
	}

	public static NotificationSettings defaultOf(User user) {
		return new NotificationSettings(user);
	}
}
