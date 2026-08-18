package com.chagok.infrastructure.security.oauth2;

import com.chagok.domain.notification.NotificationSettingsRepository;
import com.chagok.domain.subscription.SubscriptionPlan;
import com.chagok.domain.subscription.SubscriptionPlanRepository;
import com.chagok.domain.subscription.UserSubscriptionRepository;
import com.chagok.domain.user.OAuthProvider;
import com.chagok.domain.user.User;
import com.chagok.domain.user.UserOauth;
import com.chagok.domain.user.UserOauthRepository;
import com.chagok.domain.user.UserRepository;
import com.chagok.infrastructure.security.AesEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

	@InjectMocks
	private CustomOAuth2UserService customOAuth2UserService;

	@Mock
	private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserOauthRepository userOauthRepository;

	@Mock
	private NotificationSettingsRepository notificationSettingsRepository;

	@Mock
	private SubscriptionPlanRepository subscriptionPlanRepository;

	@Mock
	private UserSubscriptionRepository userSubscriptionRepository;

	@Mock
	private AesEncryptionUtil aesEncryptionUtil;

	@Test
	@DisplayName("이미 연결된 소셜 계정이면 토큰만 갱신하고 기존 유저를 반환한다")
	void should_updateTokensAndReturnExistingUser_when_oauthAlreadyLinked() {
		OAuth2UserRequest userRequest = kakaoUserRequest();
		OAuth2User kakaoUser = kakaoOAuth2User("kakao@test.com", "카카오유저");
		User existingUser = withId(User.builder().email("kakao@test.com").build(), 1L);
		UserOauth userOauth = UserOauth.of(existingUser, OAuthProvider.KAKAO, "12345", "old", null);

		given(delegate.loadUser(userRequest)).willReturn(kakaoUser);
		given(aesEncryptionUtil.encrypt(any())).willReturn("encrypted");
		given(userOauthRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
			.willReturn(Optional.of(userOauth));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertThat(result).isInstanceOf(CustomOAuth2User.class);
		assertThat(((CustomOAuth2User) result).getUser()).isEqualTo(existingUser);
		assertThat(userOauth.getAccessTokenEncrypted()).isEqualTo("encrypted");
		verify(userRepository, never()).save(any());
		verify(userOauthRepository, never()).save(any());
	}

	@Test
	@DisplayName("이메일로 기존 유저를 찾으면 새 유저를 만들지 않고 소셜 계정만 연결한다")
	void should_linkOauthToExistingUser_when_emailAlreadyRegistered() {
		OAuth2UserRequest userRequest = kakaoUserRequest();
		OAuth2User kakaoUser = kakaoOAuth2User("kakao@test.com", "카카오유저");
		User existingUser = withId(User.builder().email("kakao@test.com").build(), 1L);

		given(delegate.loadUser(userRequest)).willReturn(kakaoUser);
		given(aesEncryptionUtil.encrypt(any())).willReturn("encrypted");
		given(userOauthRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
			.willReturn(Optional.empty());
		given(userRepository.findByEmailAndDeletedAtIsNull("kakao@test.com")).willReturn(Optional.of(existingUser));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertThat(((CustomOAuth2User) result).getUser()).isEqualTo(existingUser);
		verify(userRepository, never()).save(any());
		verify(userOauthRepository).save(any(UserOauth.class));
	}

	@Test
	@DisplayName("신규 유저면 회원가입과 알림설정/구독 초기화, 소셜 계정 연결이 함께 처리된다")
	void should_createNewUserWithDefaults_when_emailNotRegistered() {
		OAuth2UserRequest userRequest = kakaoUserRequest();
		OAuth2User kakaoUser = kakaoOAuth2User("new@test.com", "신규유저");
		SubscriptionPlan freePlan = Mockito.mock(SubscriptionPlan.class);

		given(delegate.loadUser(userRequest)).willReturn(kakaoUser);
		given(aesEncryptionUtil.encrypt(any())).willReturn("encrypted");
		given(userOauthRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
			.willReturn(Optional.empty());
		given(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).willReturn(Optional.empty());
		given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(subscriptionPlanRepository.findByCode("FREE")).willReturn(Optional.of(freePlan));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		User createdUser = ((CustomOAuth2User) result).getUser();
		assertThat(createdUser.getEmail()).isEqualTo("new@test.com");
		assertThat(createdUser.getNickname()).isEqualTo("신규유저");
		verify(notificationSettingsRepository).save(any());
		verify(userSubscriptionRepository).save(any());
		verify(userOauthRepository).save(any(UserOauth.class));
	}

	@Test
	@DisplayName("FREE 플랜이 시드되어 있지 않으면 신규 유저 생성이 실패한다")
	void should_throwIllegalStateException_when_freePlanMissing() {
		OAuth2UserRequest userRequest = kakaoUserRequest();
		OAuth2User kakaoUser = kakaoOAuth2User("new@test.com", "신규유저");

		given(delegate.loadUser(userRequest)).willReturn(kakaoUser);
		given(aesEncryptionUtil.encrypt(any())).willReturn("encrypted");
		given(userOauthRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
			.willReturn(Optional.empty());
		given(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).willReturn(Optional.empty());
		given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(subscriptionPlanRepository.findByCode("FREE")).willReturn(Optional.empty());

		assertThatThrownBy(() -> customOAuth2UserService.loadUser(userRequest))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("소셜 계정에 이메일 정보가 없으면 OAuth2AuthenticationException이 발생한다")
	void should_throwOAuth2AuthenticationException_when_emailMissing() {
		OAuth2UserRequest userRequest = kakaoUserRequest();
		OAuth2User kakaoUserWithoutEmail = new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of("id", 12345L, "kakao_account", Map.of("profile", Map.of("nickname", "닉네임"))),
			"id");

		given(delegate.loadUser(userRequest)).willReturn(kakaoUserWithoutEmail);

		assertThatThrownBy(() -> customOAuth2UserService.loadUser(userRequest))
			.isInstanceOf(OAuth2AuthenticationException.class);
	}

	private OAuth2User kakaoOAuth2User(String email, String nickname) {
		return new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of(
				"id", 12345L,
				"kakao_account", Map.of(
					"email", email,
					"profile", Map.of("nickname", nickname)
				)
			),
			"id");
	}

	private OAuth2UserRequest kakaoUserRequest() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("kakao")
			.clientId("client-id")
			.clientSecret("client-secret")
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.authorizationUri("https://kauth.kakao.com/oauth/authorize")
			.tokenUri("https://kauth.kakao.com/oauth/token")
			.userInfoUri("https://kapi.kakao.com/v2/user/me")
			.userNameAttributeName("id")
			.build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(
			OAuth2AccessToken.TokenType.BEARER, "token-value", Instant.now(), Instant.now().plusSeconds(3600));
		return new OAuth2UserRequest(registration, accessToken);
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
