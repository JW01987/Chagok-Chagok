package com.chagok.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@InjectMocks
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private FilterChain filterChain;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("유효한 Bearer 토큰이면 SecurityContext에 인증 정보가 설정된다")
	void should_setAuthentication_when_tokenValid() throws Exception {
		given(request.getHeader("Authorization")).willReturn("Bearer valid-token");
		given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
		given(jwtTokenProvider.getUserId("valid-token")).willReturn(1L);

		jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("유효하지 않은 토큰이면 SecurityContext가 비어있다")
	void should_notSetAuthentication_when_tokenInvalid() throws Exception {
		given(request.getHeader("Authorization")).willReturn("Bearer invalid-token");
		given(jwtTokenProvider.validateToken("invalid-token")).willReturn(false);

		jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("Authorization 헤더가 없으면 SecurityContext가 비어있다")
	void should_notSetAuthentication_when_authorizationHeaderMissing() throws Exception {
		given(request.getHeader("Authorization")).willReturn(null);

		jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("Bearer 접두사가 없으면 SecurityContext가 비어있다")
	void should_notSetAuthentication_when_headerNotBearerFormat() throws Exception {
		given(request.getHeader("Authorization")).willReturn("Basic some-value");

		jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}
}
