package com.chagok.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

	private final SwaggerConfig swaggerConfig = new SwaggerConfig();

	@Test
	void should_configureBearerAuthAndApiInfo_when_openApiBeanCreated() {
		OpenAPI openAPI = swaggerConfig.openAPI();

		assertThat(openAPI.getInfo().getTitle()).isEqualTo("차곡차곡 API");
		assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1.0.0");
		assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
		assertThat(openAPI.getSecurity()).hasSize(1);
	}
}
