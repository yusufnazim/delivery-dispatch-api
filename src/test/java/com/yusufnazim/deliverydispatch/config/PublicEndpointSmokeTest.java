package com.yusufnazim.deliverydispatch.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicEndpointSmokeTest {

	private final MockMvc mockMvc;

	@Autowired
	PublicEndpointSmokeTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void openApiDocsArePubliclyAccessible() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Delivery Dispatch API"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.summary").value("Log in"))
				.andExpect(jsonPath("$.paths['/api/v1/orders'].post.summary")
						.value("Create a delivery order"))
				.andExpect(jsonPath("$.paths['/api/v1/orders'].post.security[0].bearerAuth").isArray())
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/availability'].patch.summary")
						.value("Update courier availability"))
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/availability'].patch.responses['403']")
						.exists())
				.andExpect(jsonPath("$.paths['/api/v1/dispatch/orders/{orderId}/auto-assign'].post.summary")
						.value("Auto-assign an order"))
				.andExpect(jsonPath("$.paths['/api/v1/dispatch/orders/{orderId}/auto-assign'].post.security[0].bearerAuth")
						.isArray())
				.andExpect(jsonPath("$.paths['/api/v1/dispatch/orders/{orderId}/assign'].post.summary")
						.value("Manually assign an order"))
				.andExpect(jsonPath("$.paths['/api/v1/dispatch/orders'].get.summary")
						.value("List operational orders"))
				.andExpect(jsonPath("$.paths['/api/v1/dispatch/couriers'].get.summary")
						.value("List operational couriers"));
	}

	@Test
	void healthEndpointIsPubliclyAccessible() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@Test
	void swaggerUiIsPubliclyAccessible() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}

	@Test
	void businessEndpointStillRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/orders"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}
}
