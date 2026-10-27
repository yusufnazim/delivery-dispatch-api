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

import com.yusufnazim.deliverydispatch.auth.AuthController;
import com.yusufnazim.deliverydispatch.auth.AuthService;
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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@WebMvcTest({AuthController.class, CourierController.class, DeliveryOrderController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class,
        RequestErrorMvcTest.ParameterValidationController.class})
class RequestErrorMvcTest {

    private final MockMvc mockMvc;

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
    RequestErrorMvcTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
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
                400, "VALIDATION_FAILED", "Request validation failed");
    }

    @Test
    void pathConstraintViolationUsesExistingValidationError() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/test/request-errors/path/0")
                        .with(authenticatedAs(Role.CUSTOMER))),
                400, "VALIDATION_FAILED", "Request validation failed");
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
    void dtoValidationKeepsItsExistingResponse() throws Exception {
        expectError(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}")),
                400, "VALIDATION_FAILED", "Request validation failed");

        verifyNoInteractions(authService);
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

    private void expectError(ResultActions result, int statusCode, String code, String message) throws Exception {
        result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"%s\",\"message\":\"%s\"}".formatted(code, message),
                        JsonCompareMode.STRICT));
    }

    private RequestPostProcessor authenticatedAs(Role role) {
        return jwt().jwt(token -> token.claim("userId", 7L).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // Exercise MVC parameter validation without shipping pagination or test endpoints in the application.
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

        @GetMapping("/invalid-response")
        @Positive
        public long invalidResponse() {
            return -1L;
        }
    }
}
