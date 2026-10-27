package com.yusufnazim.deliverydispatch.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.auth.AuthController;
import com.yusufnazim.deliverydispatch.auth.AuthService;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerRequest;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidLoginCredentialsException;
import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.courier.CourierController;
import com.yusufnazim.deliverydispatch.courier.CourierService;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderController;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderService;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.exception.OrderCancellationNotAllowedException;
import com.yusufnazim.deliverydispatch.security.SecurityErrorHandler;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@WebMvcTest({AuthController.class, CourierController.class, DeliveryOrderController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class,
        RequestErrorMvcTest.ParameterValidationController.class})
class RequestErrorMvcTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CourierService courierService;

    @MockitoBean
    private DeliveryOrderService orderService;

    @MockitoBean
    private DeliveryTimelineService timelineService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    RequestErrorMvcTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{\"password\":\"do-not-echo-this-secret\",",
            "{\"email\":\"customer@example.com\",\"password\":{\"secret\":\"do-not-echo-this-secret\"}}"})
    void unreadableLoginBodyReturnsSafeMalformedRequestError(String body) throws Exception {
        expectError(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body)),
                400, "MALFORMED_REQUEST", "Request body is missing or malformed");

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "available", ""})
    void unknownJsonEnumReturnsMalformedRequestError(String value) throws Exception {
        expectError(mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + value + "\"}")),
                400, "MALFORMED_REQUEST", "Request body is missing or malformed");

        verifyNoInteractions(courierService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    void unconvertibleOrderIdReturnsSafeParameterError(String value) throws Exception {
        expectError(mockMvc.perform(get("/api/v1/orders/{orderId}", value)
                        .with(authenticatedAs(Role.CUSTOMER))),
                400, "INVALID_REQUEST_PARAMETER", "Request parameter is missing or invalid");

        verifyNoInteractions(orderService, timelineService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "2147483648"})
    void unconvertibleQueryParameterUsesTheSameErrorContract(String value) throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/query")
                        .with(authenticatedAs(Role.CUSTOMER))
                        .param("page", value).param("status", "PENDING")),
                400, "INVALID_REQUEST_PARAMETER", "Request parameter is missing or invalid");
    }

    @Test
    void unknownQueryEnumReturnsParameterError() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/query")
                        .with(authenticatedAs(Role.CUSTOMER))
                        .param("page", "0").param("status", "UNKNOWN")),
                400, "INVALID_REQUEST_PARAMETER", "Request parameter is missing or invalid");
    }

    @Test
    void missingRequiredQueryParameterReturnsParameterError() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/query")
                        .with(authenticatedAs(Role.CUSTOMER)).param("status", "PENDING")),
                400, "INVALID_REQUEST_PARAMETER", "Request parameter is missing or invalid");
    }

    @Test
    void queryParameterConvertedToNullReturnsParameterError() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/query")
                        .with(authenticatedAs(Role.CUSTOMER))
                        .param("page", "").param("status", "PENDING")),
                400, "INVALID_REQUEST_PARAMETER", "Request parameter is missing or invalid");
    }

    @Test
    void queryConstraintViolationUsesExistingValidationError() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/query")
                        .with(authenticatedAs(Role.CUSTOMER))
                        .param("page", "-1").param("status", "PENDING")),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("page", "must be greater than or equal to 0"));
    }

    @Test
    void pathConstraintViolationUsesExistingValidationError() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/path/0")
                        .with(authenticatedAs(Role.CUSTOMER))),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("id", "must be greater than 0"));
    }

    @Test
    void validParametersStillReachTheController() throws Exception {
        mockMvc.perform(get("/api/v1/test/request-errors/query")
                        .with(authenticatedAs(Role.CUSTOMER))
                        .param("page", "0").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"page\":0,\"status\":\"PENDING\"}", JsonCompareMode.STRICT));
    }

    @Test
    void returnValueValidationRemainsAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/test/request-errors/invalid-response")
                        .with(authenticatedAs(Role.CUSTOMER)))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
                    assertThat(((HandlerMethodValidationException) result.getResolvedException()).isForReturnValue())
                            .isTrue();
                });
    }

    @Test
    void dtoValidationKeepsItsCodeAndMessageWithFieldDetails() throws Exception {
        expectError(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}")),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("email", "must not be blank"),
                new ValidationErrorDetail("password", "must not be blank"));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 80})
    void invalidRegistrationListsFieldsWithoutEchoingEmailOrPassword(int passwordLength) throws Exception {
        String password = "s".repeat(passwordLength);
        ResultActions result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "private-invalid-email", "password", password))));

        expectError(result, 400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("email", "must be a well-formed email address"),
                new ValidationErrorDetail("password", "size must be between 8 and 72"));
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain(password, "private-invalid-email");
        verifyNoInteractions(authService);
    }

    @Test
    void multipleViolationsOnTheSameFieldAreRetained() throws Exception {
        expectError(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.com\",\"password\":\"\"}")),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("password", "must not be blank"),
                new ValidationErrorDetail("password", "size must be between 8 and 72"));

        verifyNoInteractions(authService);
    }

    @Test
    void coordinateValidationListsBothFields() throws Exception {
        expectError(mockMvc.perform(patch("/api/v1/couriers/me/location")
                        .with(authenticatedAs(Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":91,\"longitude\":181}")),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("latitude", "must be less than or equal to 90.0"),
                new ValidationErrorDetail("longitude", "must be less than or equal to 180.0"));

        verifyNoInteractions(courierService);
    }

    @Test
    void availabilityValidationUsesThePublicStatusField() throws Exception {
        expectError(mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ON_DELIVERY\"}")),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("status", "status must be AVAILABLE or UNAVAILABLE"));

        verifyNoInteractions(courierService);
    }

    @Test
    void managedUserValidationUsesThePublicRoleField() throws Exception {
        expectError(mockMvc.perform(post("/api/v1/auth/users")
                        .with(authenticatedAs(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.com\",\"password\":\"StrongPass123\",\"role\":\"CUSTOMER\"}")),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("role", "role must be DISPATCHER or COURIER"));

        verifyNoInteractions(authService);
    }

    @Test
    void methodValidationUsesPublicParameterNamesWithoutEchoingTokens() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/constraints/0")
                        .with(authenticatedAs(Role.CUSTOMER))
                        .param("limit", "0").param("access_token", "secret-bearer-value")),
                400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("access_token", "size must be between 0 and 5"),
                new ValidationErrorDetail("limit", "must be greater than or equal to 1"),
                new ValidationErrorDetail("orderId", "must be greater than 0"));
    }

    @Test
    void methodValidationIncludesBodyAndQueryErrorsTogether() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/test/request-errors/body-with-parameter")
                .with(authenticatedAs(Role.CUSTOMER)).param("page", "-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid-email\",\"password\":\"shh\"}"));

        expectError(result, 400, "VALIDATION_FAILED", "Request validation failed",
                new ValidationErrorDetail("email", "must be a well-formed email address"),
                new ValidationErrorDetail("page", "must be greater than or equal to 0"),
                new ValidationErrorDetail("password", "size must be between 8 and 72"));
        assertThat(result.andReturn().getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
    }

    @Test
    void unauthenticatedMalformedRequestStillReturns401() throws Exception {
        expectError(mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content("{")),
                401, "AUTHENTICATION_REQUIRED", "Authentication is required");

        verifyNoInteractions(orderService);
    }

    @Test
    void wrongRoleStillReturns403() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/orders/11").with(authenticatedAs(Role.COURIER))),
                403, "ACCESS_DENIED", "Access is denied");

        verifyNoInteractions(orderService);
    }

    @Test
    void invalidCredentialsStillReturn401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidLoginCredentialsException());

        expectError(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.com\",\"password\":\"wrong-password\"}")),
                401, "INVALID_LOGIN_CREDENTIALS", "Invalid email or password");
    }

    @Test
    void domainConflictStillReturns409() throws Exception {
        when(orderService.cancelCustomerOrder(7L, 11L))
                .thenThrow(new OrderCancellationNotAllowedException(OrderStatus.ASSIGNED));

        expectError(mockMvc.perform(delete("/api/v1/orders/11").with(authenticatedAs(Role.CUSTOMER))),
                409, "ORDER_CANCELLATION_NOT_ALLOWED", "Order cannot be cancelled from status: ASSIGNED");
    }

    private void expectError(ResultActions result, int statusCode, String code, String message,
            ValidationErrorDetail... details) throws Exception {
        result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(
                                Map.of("code", code, "message", message, "details", List.of(details))),
                        JsonCompareMode.STRICT));
    }

    private RequestPostProcessor authenticatedAs(Role role) {
        return jwt().jwt(token -> token.claim("userId", 7L).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // Exercise validation combinations independently of production endpoint contracts.
    @TestComponent
    @RestController
    @RequestMapping("/api/v1/test/request-errors")
    static class ParameterValidationController {

        @GetMapping("/query")
        public Map<String, Object> query(@RequestParam @Min(0) Integer page, @RequestParam OrderStatus status) {
            return Map.of("page", page, "status", status);
        }

        @GetMapping("/path/{id}")
        public Long path(@PathVariable @Positive Long id) {
            return id;
        }

        @GetMapping("/constraints/{orderId}")
        public void constraints(@PathVariable("orderId") @Positive Long id,
                @RequestParam(name = "limit") @Min(1) Integer pageSize,
                @RequestParam("access_token") @Size(max = 5) String token) {
        }

        @PostMapping("/body-with-parameter")
        public void bodyWithParameter(@Valid @RequestBody RegisterCustomerRequest request,
                @RequestParam @Min(0) int page) {
        }

        @GetMapping("/invalid-response")
        @Positive
        public long invalidResponse() {
            return -1L;
        }
    }
}
