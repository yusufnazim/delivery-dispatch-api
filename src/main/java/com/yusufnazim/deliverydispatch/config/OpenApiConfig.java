package com.yusufnazim.deliverydispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	public static final String SECURITY_SCHEME_NAME = "bearerAuth";

	@Bean
	OpenAPI deliveryDispatchOpenApi() {
		return new OpenAPI()
				.components(new Components()
						.addSecuritySchemes(
								SECURITY_SCHEME_NAME,
								new SecurityScheme()
										.type(SecurityScheme.Type.HTTP)
										.scheme("bearer")
										.bearerFormat("JWT")
										.description("Use the token returned by POST /api/v1/auth/login. "
												+ "Invalid issuer, required claims, signature, or timestamps return 401. "
												+ "A valid token with insufficient role access returns 403.")))
				.info(new Info()
						.title("Delivery Dispatch API")
						.version("v1")
						.description("REST API for delivery order dispatch and courier assignment workflows."));
	}
}
