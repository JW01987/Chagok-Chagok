package com.chagok.infrastructure.security.oauth2;

import com.chagok.domain.notification.NotificationSettings;
import com.chagok.domain.notification.NotificationSettingsRepository;
import com.chagok.domain.subscription.SubscriptionPlan;
import com.chagok.domain.subscription.SubscriptionPlanRepository;
import com.chagok.domain.subscription.UserSubscription;
import com.chagok.domain.subscription.UserSubscriptionRepository;
import com.chagok.domain.user.OAuthProvider;
import com.chagok.domain.user.User;
import com.chagok.domain.user.UserOauth;
import com.chagok.domain.user.UserOauthRepository;
import com.chagok.domain.user.UserRepository;
import com.chagok.domain.user.UserStatus;
import com.chagok.infrastructure.security.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private static final String FREE_PLAN_CODE = "FREE";

	private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
	private final UserRepository userRepository;
	private final UserOauthRepository userOauthRepository;
	private final NotificationSettingsRepository notificationSettingsRepository;
	private final SubscriptionPlanRepository subscriptionPlanRepository;
	private final UserSubscriptionRepository userSubscriptionRepository;
	private final AesEncryptionUtil aesEncryptionUtil;

	@Override
	@Transactional
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oAuth2User = delegate.loadUser(userRequest);
		OAuthProvider provider = OAuthProvider.valueOf(
			userRequest.getClientRegistration().getRegistrationId().toUpperCase());

		OAuthUserInfo userInfo = OAuthUserInfoFactory.getOAuthUserInfo(provider.name(), oAuth2User.getAttributes());
		if (userInfo.getEmail() == null) {
			throw new OAuth2AuthenticationException(
				new OAuth2Error("email_required", "소셜 로그인 계정에서 이메일 정보를 가져올 수 없습니다.", null));
		}

		String encryptedAccessToken = aesEncryptionUtil.encrypt(userRequest.getAccessToken().getTokenValue());
		LocalDateTime tokenExpiresAt = toLocalDateTime(userRequest.getAccessToken().getExpiresAt());

		UserOauth userOauth = userOauthRepository
			.findByProviderAndProviderUserId(provider, userInfo.getProviderId())
			.orElse(null);

		User user;
		if (userOauth != null) {
			user = userOauth.getUser();
			userOauth.updateTokens(encryptedAccessToken, tokenExpiresAt);
		} else {
			user = userRepository.findByEmailAndDeletedAtIsNull(userInfo.getEmail())
				.orElseGet(() -> createNewUser(userInfo));
			userOauthRepository.save(
				UserOauth.of(user, provider, userInfo.getProviderId(), encryptedAccessToken, tokenExpiresAt));
		}

		return new CustomOAuth2User(user, oAuth2User.getAttributes());
	}

	private User createNewUser(OAuthUserInfo userInfo) {
		User newUser = User.builder()
			.email(userInfo.getEmail())
			.nickname(userInfo.getNickname())
			.status(UserStatus.ACTIVE)
			.build();
		userRepository.save(newUser);

		notificationSettingsRepository.save(NotificationSettings.defaultOf(newUser));

		SubscriptionPlan freePlan = subscriptionPlanRepository.findByCode(FREE_PLAN_CODE)
			.orElseThrow(() -> new IllegalStateException("FREE 플랜이 없습니다"));
		userSubscriptionRepository.save(UserSubscription.freeOf(newUser, freePlan));

		return newUser;
	}

	private LocalDateTime toLocalDateTime(Instant instant) {
		return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}
}
