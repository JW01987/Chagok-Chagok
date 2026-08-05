package com.chagok.presentation.auth;

import com.chagok.application.auth.AuthService;
import com.chagok.infrastructure.security.JwtAuthenticationFilter;
import com.chagok.infrastructure.security.JwtTokenProvider;
import com.chagok.infrastructure.security.SecurityConfig;
import com.chagok.presentation.auth.dto.LoginResponse;
import com.chagok.presentation.auth.dto.ReissueResponse;
import com.chagok.presentation.auth.dto.SignupResponse;
import com.chagok.presentation.common.exception.BusinessException;
import com.chagok.presentation.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@Test
	@DisplayName("회원가입 성공 시 201과 사용자 정보를 반환한다")
	void should_return201WithUserInfo_when_signupSucceeds() throws Exception {
		given(authService.signup(any())).willReturn(new SignupResponse(1L, "test@test.com", "진"));

		mockMvc.perform(post("/api/auth/signup")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new SignupPayload("test@test.com", "password1", "진"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.userId").value(1))
			.andExpect(jsonPath("$.data.email").value("test@test.com"));
	}

	@Test
	@DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
	void should_return400_when_signupEmailInvalid() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new SignupPayload("invalid-email", "password1", "진"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	@DisplayName("이메일이 중복되면 409를 반환한다")
	void should_return409_when_signupEmailDuplicate() throws Exception {
		given(authService.signup(any())).willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

		mockMvc.perform(post("/api/auth/signup")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new SignupPayload("test@test.com", "password1", "진"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("AUTH_001"));
	}

	@Test
	@DisplayName("로그인 성공 시 토큰이 포함된 응답을 반환한다")
	void should_return200WithTokens_when_loginSucceeds() throws Exception {
		given(authService.login(any(), any(), any())).willReturn(new LoginResponse("access", "refresh", 1L));

		mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new LoginPayload("test@test.com", "password1"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").value("access"))
			.andExpect(jsonPath("$.data.refreshToken").value("refresh"));
	}

	@Test
	@DisplayName("비밀번호가 틀리면 401을 반환한다")
	void should_return401_when_loginPasswordInvalid() throws Exception {
		given(authService.login(any(), any(), any())).willThrow(new BusinessException(ErrorCode.INVALID_PASSWORD));

		mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new LoginPayload("test@test.com", "wrong"))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("토큰 재발급 성공 시 새 토큰을 반환한다")
	void should_return200WithNewTokens_when_reissueSucceeds() throws Exception {
		given(authService.reissue(any())).willReturn(new ReissueResponse("new-access", "new-refresh"));

		mockMvc.perform(post("/api/auth/reissue")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ReissuePayload("old-refresh"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").value("new-access"));
	}

	@Test
	@DisplayName("인증 없이 로그아웃을 호출하면 403을 반환한다")
	void should_return403_when_logoutCalledWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/auth/logout")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ReissuePayload("refresh-token"))))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("인증된 사용자가 로그아웃하면 200을 반환한다")
	void should_return200_when_logoutCalledWithValidToken() throws Exception {
		given(jwtTokenProvider.validateToken("valid-access-token")).willReturn(true);
		given(jwtTokenProvider.getUserId("valid-access-token")).willReturn(1L);

		mockMvc.perform(post("/api/auth/logout")
				.header("Authorization", "Bearer valid-access-token")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ReissuePayload("refresh-token"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));
	}

	private record SignupPayload(String email, String password, String nickname) {
	}

	private record LoginPayload(String email, String password) {
	}

	private record ReissuePayload(String refreshToken) {
	}
}
