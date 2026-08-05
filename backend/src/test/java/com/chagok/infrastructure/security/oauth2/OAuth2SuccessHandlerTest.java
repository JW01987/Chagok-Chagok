package com.chagok.infrastructure.security.oauth2;

import com.chagok.domain.auth.RefreshToken;
import com.chagok.domain.auth.RefreshTokenRepository;
import com.chagok.domain.user.User;
import com.chagok.domain.user.UserStatus;
import com.chagok.infrastructure.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

	@InjectMocks
	private OAuth2SuccessHandler oAuth2SuccessHandler;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private Authentication authentication;

	@Test
	@DisplayName("인증 성공 시 JWT 토큰을 발급하고 JSON 응답을 작성한다")
	void should_issueTokensAndWriteJsonResponse_when_authenticationSucceeds() throws Exception {
		User user = withId(User.builder().email("test@test.com").status(UserStatus.ACTIVE).build(), 1L);
		CustomOAuth2User principal = new CustomOAuth2User(user, java.util.Map.of("id", 12345L));

		StringWriter stringWriter = new StringWriter();
		given(response.getWriter()).willReturn(new PrintWriter(stringWriter));
		given(authentication.getPrincipal()).willReturn(principal);
		given(request.getRemoteAddr()).willReturn("127.0.0.1");
		given(request.getHeader("User-Agent")).willReturn("agent");
		given(jwtTokenProvider.generateAccessToken(1L, "test@test.com")).willReturn("access-token");
		given(jwtTokenProvider.generateRefreshToken(1L)).willReturn("refresh-token");
		given(jwtTokenProvider.getExpiration("refresh-token")).willReturn(LocalDateTime.now().plusDays(30));

		oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

		verify(response).setContentType("application/json;charset=UTF-8");
		verify(refreshTokenRepository).save(any(RefreshToken.class));
		assertThat(stringWriter.toString())
			.contains("\"accessToken\":\"access-token\"")
			.contains("\"refreshToken\":\"refresh-token\"");
	}

	private User withId(User user, Long id) {
		try {
			Field field = User.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(user, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return user;
	}
}
