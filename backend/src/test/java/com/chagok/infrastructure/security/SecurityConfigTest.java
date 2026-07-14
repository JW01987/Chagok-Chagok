package com.chagok.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProtectedTestController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

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
