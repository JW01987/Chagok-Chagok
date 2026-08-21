package com.chagok.application.auth;

import com.chagok.application.user.OnboardingService;
import com.chagok.domain.auth.RefreshToken;
import com.chagok.domain.auth.RefreshTokenRepository;
import com.chagok.domain.notification.NotificationSettingsRepository;
import com.chagok.domain.subscription.SubscriptionPlan;
import com.chagok.domain.subscription.SubscriptionPlanRepository;
import com.chagok.domain.subscription.UserSubscriptionRepository;
import com.chagok.domain.user.GoalType;
import com.chagok.domain.user.InvestmentType;
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
import com.chagok.presentation.user.dto.OnboardingRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@InjectMocks
	private AuthService authService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private NotificationSettingsRepository notificationSettingsRepository;

	@Mock
	private SubscriptionPlanRepository subscriptionPlanRepository;

	@Mock
	private UserSubscriptionRepository userSubscriptionRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private OnboardingService onboardingService;

	@Test
	@DisplayName("이메일 중복 시 DUPLICATE_EMAIL 예외 발생")
	void should_throwDuplicateEmailException_when_emailAlreadyExists() {
		given(userRepository.existsByEmailAndDeletedAtIsNull("test@test.com")).willReturn(true);

		assertThatThrownBy(() -> authService.signup(new SignupRequest("test@test.com", "password1", "진", null)))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("중복");

		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("정상 요청 시 회원가입과 알림설정/구독 초기화가 함께 처리된다")
	void should_createUserWithDefaults_when_signupRequestValid() {
		SignupRequest request = new SignupRequest("test@test.com", "password1", "진", null);
		SubscriptionPlan freePlan = org.mockito.Mockito.mock(SubscriptionPlan.class);

		given(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())).willReturn(false);
		given(passwordEncoder.encode(request.getPassword())).willReturn("encoded");
		given(userRepository.save(any(User.class))).willAnswer(invocation -> {
			User savedUser = invocation.getArgument(0);
			setField(savedUser, "id", 1L);
			return savedUser;
		});
		given(subscriptionPlanRepository.findByCode("FREE")).willReturn(Optional.of(freePlan));

		SignupResponse response = authService.signup(request);

		assertThat(response.getUserId()).isEqualTo(1L);
		assertThat(response.getEmail()).isEqualTo(request.getEmail());
		assertThat(response.getNickname()).isEqualTo(request.getNickname());
		verify(notificationSettingsRepository).save(any());
		verify(userSubscriptionRepository).save(any());
		verify(onboardingService, never()).saveOnboarding(any(), any());
	}

	@Test
	@DisplayName("게스트 온보딩 데이터가 있으면 회원가입 시 자동으로 마이그레이션된다")
	void should_migrateOnboarding_when_signupRequestHasOnboarding() {
		OnboardingRequest onboardingRequest =
			new OnboardingRequest(InvestmentType.BALANCED, 100000, GoalType.FREE, null, null, null);
		SignupRequest request = new SignupRequest("test@test.com", "password1", "진", onboardingRequest);
		SubscriptionPlan freePlan = org.mockito.Mockito.mock(SubscriptionPlan.class);

		given(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())).willReturn(false);
		given(passwordEncoder.encode(request.getPassword())).willReturn("encoded");
		given(userRepository.save(any(User.class))).willAnswer(invocation -> {
			User savedUser = invocation.getArgument(0);
			setField(savedUser, "id", 1L);
			return savedUser;
		});
		given(subscriptionPlanRepository.findByCode("FREE")).willReturn(Optional.of(freePlan));

		authService.signup(request);

		verify(onboardingService).saveOnboarding(1L, onboardingRequest);
	}

	@Test
	@DisplayName("FREE 플랜이 시드되어 있지 않으면 회원가입이 실패한다")
	void should_throwIllegalStateException_when_freePlanMissing() {
		SignupRequest request = new SignupRequest("test@test.com", "password1", "진", null);
		given(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())).willReturn(false);
		given(passwordEncoder.encode(request.getPassword())).willReturn("encoded");
		given(userRepository.save(any(User.class))).willReturn(withId(User.builder().build(), 1L));
		given(subscriptionPlanRepository.findByCode("FREE")).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.signup(request))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("존재하지 않는 이메일로 로그인하면 USER_NOT_FOUND 예외 발생")
	void should_throwUserNotFoundException_when_emailNotRegistered() {
		given(userRepository.findByEmailAndDeletedAtIsNull("test@test.com")).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("test@test.com", "password1"), "127.0.0.1", "agent"))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("잠긴 계정으로 로그인하면 ACCOUNT_LOCKED 예외 발생")
	void should_throwAccountLockedException_when_accountIsLocked() {
		User lockedUser = User.builder()
			.email("test@test.com")
			.passwordHash("hash")
			.status(UserStatus.LOCKED)
			.build();
		setField(lockedUser, "lockedUntil", LocalDateTime.now().plusMinutes(10));
		given(userRepository.findByEmailAndDeletedAtIsNull("test@test.com")).willReturn(Optional.of(lockedUser));

		assertThatThrownBy(() -> authService.login(new LoginRequest("test@test.com", "password1"), null, null))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED);
	}

	@Test
	@DisplayName("비밀번호가 틀리면 실패 횟수가 증가하고 INVALID_PASSWORD 예외 발생")
	void should_increaseFailCountAndThrow_when_passwordDoesNotMatch() {
		User user = User.builder().email("test@test.com").passwordHash("hash")
			.status(UserStatus.ACTIVE).build();
		given(userRepository.findByEmailAndDeletedAtIsNull(any())).willReturn(Optional.of(user));
		given(passwordEncoder.matches(any(), any())).willReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("test@test.com", "wrong"), null, null))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);

		assertThat(user.getLoginFailCount()).isEqualTo((short) 1);
		verify(userRepository).save(user);
	}

	@Test
	@DisplayName("5회 로그인 실패 시 계정 잠금")
	void should_lockAccount_when_fiveLoginFailures() {
		User user = User.builder().email("test@test.com").passwordHash("hash")
			.status(UserStatus.ACTIVE).loginFailCount(4).build();
		given(userRepository.findByEmailAndDeletedAtIsNull(any())).willReturn(Optional.of(user));
		given(passwordEncoder.matches(any(), any())).willReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("test@test.com", "wrong"), null, null))
			.isInstanceOf(BusinessException.class);

		assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
	}

	@Test
	@DisplayName("정상 로그인 시 토큰이 발급되고 리프레시 토큰이 저장된다")
	void should_issueTokensAndSaveRefreshToken_when_loginSucceeds() {
		User user = withId(User.builder().email("test@test.com").passwordHash("hash")
			.status(UserStatus.ACTIVE).build(), 1L);
		given(userRepository.findByEmailAndDeletedAtIsNull("test@test.com")).willReturn(Optional.of(user));
		given(passwordEncoder.matches("password1", "hash")).willReturn(true);
		given(jwtTokenProvider.generateAccessToken(1L, "test@test.com")).willReturn("access-token");
		given(jwtTokenProvider.generateRefreshToken(1L)).willReturn("refresh-token");
		given(jwtTokenProvider.getExpiration("refresh-token")).willReturn(LocalDateTime.now().plusDays(30));

		LoginResponse response = authService.login(new LoginRequest("test@test.com", "password1"), "127.0.0.1", "agent");

		assertThat(response.getAccessToken()).isEqualTo("access-token");
		assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
		assertThat(response.getUserId()).isEqualTo(1L);
		assertThat(user.getLoginFailCount()).isEqualTo((short) 0);
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	@DisplayName("유효하지 않은 리프레시 토큰으로 재발급하면 INVALID_REFRESH_TOKEN 예외 발생")
	void should_throwInvalidRefreshTokenException_when_tokenSignatureInvalid() {
		given(jwtTokenProvider.validateToken("invalid")).willReturn(false);

		assertThatThrownBy(() -> authService.reissue(new ReissueRequest("invalid")))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
	}

	@Test
	@DisplayName("DB에 존재하지 않는 리프레시 토큰이면 INVALID_REFRESH_TOKEN 예외 발생")
	void should_throwInvalidRefreshTokenException_when_tokenNotStored() {
		given(jwtTokenProvider.validateToken("token")).willReturn(true);
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.reissue(new ReissueRequest("token")))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
	}

	@Test
	@DisplayName("이미 무효화된 리프레시 토큰이면 REVOKED_REFRESH_TOKEN 예외 발생")
	void should_throwRevokedRefreshTokenException_when_tokenAlreadyRevoked() {
		User user = withId(User.builder().email("test@test.com").build(), 1L);
		RefreshToken storedToken = RefreshToken.of(user, "hash", null, null, LocalDateTime.now().plusDays(1));
		storedToken.revoke();

		given(jwtTokenProvider.validateToken("token")).willReturn(true);
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(storedToken));

		assertThatThrownBy(() -> authService.reissue(new ReissueRequest("token")))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVOKED_REFRESH_TOKEN);
	}

	@Test
	@DisplayName("정상적인 재발급 요청은 토큰 로테이션을 수행한다")
	void should_rotateTokens_when_reissueSucceeds() {
		User user = withId(User.builder().email("test@test.com").build(), 1L);
		RefreshToken storedToken = RefreshToken.of(user, "old-hash", null, null, LocalDateTime.now().plusDays(1));

		given(jwtTokenProvider.validateToken("old-token")).willReturn(true);
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(storedToken));
		given(jwtTokenProvider.generateAccessToken(anyLong(), anyString())).willReturn("new-access");
		given(jwtTokenProvider.generateRefreshToken(anyLong())).willReturn("new-refresh");
		given(jwtTokenProvider.getExpiration("new-refresh")).willReturn(LocalDateTime.now().plusDays(30));

		ReissueResponse response = authService.reissue(new ReissueRequest("old-token"));

		assertThat(response.getAccessToken()).isEqualTo("new-access");
		assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
		assertThat(storedToken.isRevoked()).isTrue();
		verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
	}

	@Test
	@DisplayName("로그아웃 시 저장된 토큰이 있으면 무효화한다")
	void should_revokeStoredToken_when_logoutCalledWithExistingToken() {
		User user = withId(User.builder().email("test@test.com").build(), 1L);
		RefreshToken storedToken = RefreshToken.of(user, "hash", null, null, LocalDateTime.now().plusDays(1));
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(storedToken));

		authService.logout(new LogoutRequest("token"));

		assertThat(storedToken.isRevoked()).isTrue();
		verify(refreshTokenRepository).save(storedToken);
	}

	@Test
	@DisplayName("로그아웃 시 저장된 토큰이 없으면 아무 작업도 하지 않는다")
	void should_doNothing_when_logoutCalledWithUnknownToken() {
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

		authService.logout(new LogoutRequest("unknown"));

		verify(refreshTokenRepository, never()).save(any());
	}

	private User withId(User user, Long id) {
		setField(user, "id", id);
		return user;
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
