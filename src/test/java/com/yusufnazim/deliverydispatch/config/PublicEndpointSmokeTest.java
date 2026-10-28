package com.yusufnazim.deliverydispatch.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
				.andExpect(jsonPath("$.paths['/api/v1/test/request-errors/query']").doesNotExist())
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
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/availability'].patch.responses['409']")
						.exists())
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/location'].patch.responses['409']")
						.exists())
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/orders/current'].get.summary")
						.value("Get current delivery assignment"))
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/orders/current'].get.security[0].bearerAuth")
						.isArray())
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/orders/current'].get.responses['200'].content['*/*'].schema['$ref']")
						.value("#/components/schemas/DeliveryOrderResponse"))
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/orders/current'].get.responses['204']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/orders/current'].get.responses['204'].content")
						.doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/orders/current'].get.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/couriers/me/orders/current'].get.responses['403']").exists())
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

	@ParameterizedTest
	@CsvSource({
			"get,/api/v1/orders,INVALID_REQUEST_PARAMETER",
			"get,/api/v1/orders,VALIDATION_FAILED",
			"get,/api/v1/dispatch/orders,INVALID_REQUEST_PARAMETER",
			"get,/api/v1/dispatch/orders,VALIDATION_FAILED",
			"get,/api/v1/dispatch/couriers,INVALID_REQUEST_PARAMETER",
			"get,/api/v1/dispatch/couriers,VALIDATION_FAILED",
			"get,/api/v1/orders/{orderId},INVALID_REQUEST_PARAMETER",
			"delete,/api/v1/orders/{orderId},INVALID_REQUEST_PARAMETER",
			"get,/api/v1/orders/{orderId}/timeline,INVALID_REQUEST_PARAMETER",
			"post,/api/v1/dispatch/orders/{orderId}/auto-assign,INVALID_REQUEST_PARAMETER",
			"post,/api/v1/dispatch/orders/{orderId}/assign,INVALID_REQUEST_PARAMETER",
			"post,/api/v1/couriers/me/orders/{orderId}/pickup,INVALID_REQUEST_PARAMETER",
			"post,/api/v1/couriers/me/orders/{orderId}/deliver,INVALID_REQUEST_PARAMETER",
			"post,/api/v1/auth/register,MALFORMED_REQUEST",
			"post,/api/v1/auth/login,MALFORMED_REQUEST",
			"post,/api/v1/auth/users,MALFORMED_REQUEST",
			"post,/api/v1/orders,MALFORMED_REQUEST",
			"post,/api/v1/dispatch/orders/{orderId}/assign,MALFORMED_REQUEST",
			"patch,/api/v1/couriers/me/availability,MALFORMED_REQUEST",
			"patch,/api/v1/couriers/me/location,MALFORMED_REQUEST"
	})
	void openApiDocumentsRequestErrors(String method, String path, String errorCode) throws Exception {
		String responsePath = "$.paths['" + path + "']." + method + ".responses['400']";

		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(responsePath + ".description").value(containsString(errorCode)))
				.andExpect(jsonPath(responsePath + ".content['*/*'].schema['$ref']")
						.value("#/components/schemas/ApiErrorResponse"));
	}

	@ParameterizedTest
	@CsvSource({
			"/api/v1/orders,PageResponseDeliveryOrderResponse,DeliveryOrderResponse",
			"/api/v1/dispatch/orders,PageResponseOperationalOrderResponse,OperationalOrderResponse",
			"/api/v1/dispatch/couriers,PageResponseOperationalCourierResponse,OperationalCourierResponse"
	})
	void openApiDocumentsListPagination(String path, String pageSchema, String itemSchema) throws Exception {
		String operationPath = "$.paths['" + path + "'].get";
		String schemaPath = "$.components.schemas." + pageSchema;

		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'page')].schema.default")
						.value(hasItems(0)))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'page')].schema.minimum")
						.value(hasItems(0)))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'page')].schema.maximum")
						.value(hasItems(21474836)))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'size')].schema.default")
						.value(hasItems(20)))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'size')].schema.minimum")
						.value(hasItems(1)))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'size')].schema.maximum")
						.value(hasItems(100)))
				.andExpect(jsonPath(operationPath + ".parameters[*].required")
						.value(everyItem(is(false))))
				.andExpect(jsonPath(operationPath + ".responses['200'].content['*/*'].schema['$ref']")
						.value("#/components/schemas/" + pageSchema))
				.andExpect(jsonPath(schemaPath + ".required")
						.value(hasItems("items", "page", "size", "totalElements", "totalPages")))
				.andExpect(jsonPath(schemaPath + ".properties.items.items['$ref']")
						.value("#/components/schemas/" + itemSchema))
				.andExpect(jsonPath(schemaPath + ".properties.totalElements.format")
						.value("int64"))
				.andExpect(jsonPath(schemaPath + ".properties.pageable").doesNotExist());
	}

	@Test
	void openApiDocumentsOperationalOrderStatusFilterAndAccess() throws Exception {
		String operationPath = "$.paths['/api/v1/dispatch/orders'].get";

		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'status')].schema.type")
						.value(hasItems("string")))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'status')].schema.enum[*]")
						.value(hasItems("PENDING", "ASSIGNED", "PICKED_UP", "DELIVERED", "CANCELLED")))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'status')].description")
						.value(hasItems(containsString("omitted or empty means all statuses"))))
				.andExpect(jsonPath(operationPath + ".security[0].bearerAuth").isArray())
				.andExpect(jsonPath(operationPath + ".responses['401']").exists())
				.andExpect(jsonPath(operationPath + ".responses['403'].description")
						.value(containsString("dispatcher or administrator")));
	}

	@Test
	void openApiDocumentsOperationalCourierAvailabilityFilterAndAccess() throws Exception {
		String operationPath = "$.paths['/api/v1/dispatch/couriers'].get";

		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'availability')].schema.type")
						.value(hasItems("string")))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'availability')].schema.enum[*]")
						.value(hasItems("AVAILABLE", "UNAVAILABLE", "ON_DELIVERY")))
				.andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'availability')].description")
						.value(hasItems(containsString("omitted or empty means all availability states"))))
				.andExpect(jsonPath(operationPath + ".security[0].bearerAuth").isArray())
				.andExpect(jsonPath(operationPath + ".responses['401']").exists())
				.andExpect(jsonPath(operationPath + ".responses['403'].description")
						.value(containsString("dispatcher or administrator")));
	}

	@Test
	void swaggerUiIsPubliclyAccessible() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}

	@Test
	void openApiDocumentsValidationDetailsAndEmptyDetailsForOtherErrors() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.ApiErrorResponse.required").value(hasItems("details")))
				.andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.details.type").value("array"))
				.andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.details.description")
						.value(containsString("empty for non-validation errors")))
				.andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.details.items['$ref']")
						.value("#/components/schemas/ValidationErrorDetail"))
				.andExpect(jsonPath("$.components.schemas.ValidationErrorDetail.required").value(hasItems("field", "message")))
				.andExpect(jsonPath("$.components.schemas.ValidationErrorDetail.properties.field.type").value("string"))
				.andExpect(jsonPath("$.components.schemas.ValidationErrorDetail.properties.message.type").value("string"));
	}

	@Test
	void businessEndpointStillRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/orders"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}
}
