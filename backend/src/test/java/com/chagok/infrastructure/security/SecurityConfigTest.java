package com.chagok.infrastructure.security;

import com.chagok.infrastructure.security.oauth2.CustomOAuth2UserService;
import com.chagok.infrastructure.security.oauth2.OAuth2SuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProtectedTestController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private CustomOAuth2UserService customOAuth2UserService;

	@MockitoBean
	private OAuth2SuccessHandler oAuth2SuccessHandler;

	@Test
	void should_reject_when_protectedEndpointCalledWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/protected"))
			.andExpect(status().isForbidden());
	}

	@RestController
	static class ProtectedTestController {

		@GetMapping("/protected")
		public String protectedEndpoint() {
			return "ok";
		}
	}
}
