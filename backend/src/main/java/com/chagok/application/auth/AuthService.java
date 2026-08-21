package com.chagok.application.auth;

import com.chagok.application.user.OnboardingService;
import com.chagok.domain.auth.RefreshToken;
import com.chagok.domain.auth.RefreshTokenRepository;
import com.chagok.domain.notification.NotificationSettings;
import com.chagok.domain.notification.NotificationSettingsRepository;
import com.chagok.domain.subscription.SubscriptionPlan;
import com.chagok.domain.subscription.SubscriptionPlanRepository;
import com.chagok.domain.subscription.UserSubscription;
import com.chagok.domain.subscription.UserSubscriptionRepository;
import com.chagok.domain.user.User;
import com.chagok.domain.user.UserRepository;
import com.chagok.domain.user.UserStatus;
import com.chagok.infrastructure.security.JwtTokenProvider;
import com.chagok.presentation.auth.dto.LoginRequest;
import com.chagok.presentation.auth.dto.LoginResponse;
import com.chagok.presentation.auth.dto.LogoutRequest;
import com.chagok.presentation.auth.dto.ReissueRequest;
import com.chagok.presentation.auth.dto.ReissueResponse;
import com.chagok.presentation.auth.dto.SignupRequest;
import com.chagok.presentation.auth.dto.SignupResponse;
import com.chagok.presentation.common.exception.BusinessException;
import com.chagok.presentation.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String FREE_PLAN_CODE = "FREE";

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final NotificationSettingsRepository notificationSettingsRepository;
	private final SubscriptionPlanRepository subscriptionPlanRepository;
	private final UserSubscriptionRepository userSubscriptionRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final OnboardingService onboardingService;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}

		User user = User.builder()
			.email(request.getEmail())
			.passwordHash(passwordEncoder.encode(request.getPassword()))
			.nickname(request.getNickname())
			.status(UserStatus.ACTIVE)
			.build();

		userRepository.save(user);

		notificationSettingsRepository.save(NotificationSettings.defaultOf(user));

		SubscriptionPlan freePlan = subscriptionPlanRepository.findByCode(FREE_PLAN_CODE)
			.orElseThrow(() -> new IllegalStateException("FREE 플랜이 없습니다"));
		userSubscriptionRepository.save(UserSubscription.freeOf(user, freePlan));

		// 게스트 온보딩 데이터가 있으면 회원가입과 함께 자동으로 서버에 저장(마이그레이션)한다.
		if (request.getOnboarding() != null) {
			onboardingService.saveOnboarding(user.getId(), request.getOnboarding());
		}

		return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
	}

	@Transactional
	public LoginResponse login(LoginRequest request, String ip, String deviceInfo) {
		User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		if (user.isLocked()) {
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
		}

		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			user.increaseLoginFailCount();
			userRepository.save(user);
			throw new BusinessException(ErrorCode.INVALID_PASSWORD);
		}

		user.resetLoginFailCount();
		user.updateLastLoginAt();
		userRepository.save(user);

		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

		String tokenHash = hashToken(refreshToken);
		refreshTokenRepository.save(
			RefreshToken.of(user, tokenHash, ip, deviceInfo, jwtTokenProvider.getExpiration(refreshToken)));

		return new LoginResponse(accessToken, refreshToken, user.getId());
	}

	@Transactional
	public ReissueResponse reissue(ReissueRequest request) {
		if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		String tokenHash = hashToken(request.getRefreshToken());
		RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		if (storedToken.isRevoked()) {
			throw new BusinessException(ErrorCode.REVOKED_REFRESH_TOKEN);
		}

		storedToken.revoke();
		refreshTokenRepository.save(storedToken);

		User user = storedToken.getUser();
		String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
		String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
		refreshTokenRepository.save(RefreshToken.of(
			user, hashToken(newRefreshToken), null, null, jwtTokenProvider.getExpiration(newRefreshToken)));

		return new ReissueResponse(newAccessToken, newRefreshToken);
	}

	@Transactional
	public void logout(LogoutRequest request) {
		String tokenHash = hashToken(request.getRefreshToken());
		refreshTokenRepository.findByTokenHash(tokenHash)
			.ifPresent(token -> {
				token.revoke();
				refreshTokenRepository.save(token);
			});
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
		}
	}
}
