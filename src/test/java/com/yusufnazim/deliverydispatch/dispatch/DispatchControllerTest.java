package com.yusufnazim.deliverydispatch.dispatch;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.dispatch.dto.DispatchAssignmentResponse;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierNotEligibleForDispatchException;
import com.yusufnazim.deliverydispatch.dispatch.exception.NoEligibleCourierException;
import com.yusufnazim.deliverydispatch.dto.PageResponse;
import com.yusufnazim.deliverydispatch.exception.GlobalExceptionHandler;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderService;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.dto.OperationalOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentConflictException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.security.SecurityErrorHandler;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(DispatchController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
class DispatchControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private DispatchService dispatchService;

    @MockitoBean
    private DeliveryOrderService deliveryOrderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    DispatchControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void autoAssignReturnsSelectedCourierForDispatcher() throws Exception {
        DispatchAssignmentResponse response = new DispatchAssignmentResponse(11L, 7L, OrderStatus.ASSIGNED);
        when(dispatchService.autoAssignOrder(11L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/orders/11/auto-assign")
                        .with(authenticatedAs(Role.DISPATCHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(11))
                .andExpect(jsonPath("$.courierId").value(7))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        verify(dispatchService).autoAssignOrder(11L);
    }

    @Test
    void listOperationalOrdersReturnsOrdersForDispatcher() throws Exception {
        when(deliveryOrderService.listOperationalOrders(null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(operationalOrderResponse()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/dispatch/orders")
                        .with(authenticatedAs(Role.DISPATCHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(11))
                .andExpect(jsonPath("$.items[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.items[0].customerId").value(3))
                .andExpect(jsonPath("$.items[0].customerEmail").value("customer@example.com"))
                .andExpect(jsonPath("$.items[0].courierId").value(7))
                .andExpect(jsonPath("$.items[0].courierEmail").value("courier@example.com"))
                .andExpect(jsonPath("$.items[0].courierDisplayName").value("Ayse Courier"))
                .andExpect(jsonPath("$.items[0].pickupAddress").value("Pickup"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(deliveryOrderService).listOperationalOrders(null, 0, 20);
    }

    @Test
    void listOperationalOrdersAllowsAdmin() throws Exception {
        when(deliveryOrderService.listOperationalOrders(null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/orders")
                        .with(authenticatedAs(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
                        """, JsonCompareMode.STRICT));

        verify(deliveryOrderService).listOperationalOrders(null, 0, 20);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void listOperationalOrdersAcceptsEachStatusFilter(OrderStatus orderStatus) throws Exception {
        when(deliveryOrderService.listOperationalOrders(orderStatus, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.DISPATCHER))
                        .param("status", orderStatus.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(deliveryOrderService).listOperationalOrders(orderStatus, 0, 20);
    }

    @Test
    void listOperationalOrdersAllowsAdminToFilterAndPageResults() throws Exception {
        when(deliveryOrderService.listOperationalOrders(OrderStatus.ASSIGNED, 2, 10))
                .thenReturn(new PageResponse<>(List.of(operationalOrderResponse()), 2, 10, 21, 3));

        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.ADMIN))
                        .param("status", "ASSIGNED").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(deliveryOrderService).listOperationalOrders(OrderStatus.ASSIGNED, 2, 10);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "21474836,100"})
    void listOperationalOrdersAcceptsBoundsWithEmptyStatusAsNoFilter(int page, int size) throws Exception {
        when(deliveryOrderService.listOperationalOrders(null, page, size))
                .thenReturn(new PageResponse<>(List.of(), page, size, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.DISPATCHER))
                        .param("status", "").param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size));

        verify(deliveryOrderService).listOperationalOrders(null, page, size);
    }

    @ParameterizedTest
    @CsvSource({"page,3,3,20", "size,10,0,10"})
    void listOperationalOrdersDefaultsEachOmittedParameterIndependently(String parameter, String value, int page, int size)
            throws Exception {
        when(deliveryOrderService.listOperationalOrders(null, page, size))
                .thenReturn(new PageResponse<>(List.of(), page, size, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.DISPATCHER)).param(parameter, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size));

        verify(deliveryOrderService).listOperationalOrders(null, page, size);
    }

    @Test
    void listOperationalOrdersKeepsFilteredTotalsBeyondTheLastPage() throws Exception {
        when(deliveryOrderService.listOperationalOrders(OrderStatus.PENDING, 3, 2))
                .thenReturn(new PageResponse<>(List.of(), 3, 2, 5, 3));

        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.DISPATCHER))
                        .param("status", "PENDING").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items":[],"page":3,"size":2,"totalElements":5,"totalPages":3}
                        """, JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @CsvSource({
            "status,UNKNOWN", "status,pending", "status,1",
            "page,abc", "size,abc", "page,1.5", "size,1.5", "page,2147483648", "size,2147483648"
    })
    void listOperationalOrdersRejectsInvalidEnumAndNonIntegerParameters(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.DISPATCHER)).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST_PARAMETER","message":"Request parameter is missing or invalid","details":[]}
                        """, JsonCompareMode.STRICT));

        verifyNoInteractions(deliveryOrderService, dispatchService);
    }

    @ParameterizedTest
    @CsvSource({
            "page,-1,must be greater than or equal to 0",
            "page,21474837,must be less than or equal to 21474836",
            "size,0,must be greater than or equal to 1",
            "size,-1,must be greater than or equal to 1",
            "size,101,must be less than or equal to 100"
    })
    void listOperationalOrdersRejectsOutOfRangeParameters(String parameter, String value, String message) throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.DISPATCHER)).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value(parameter))
                .andExpect(jsonPath("$.details[0].message").value(message));

        verifyNoInteractions(deliveryOrderService, dispatchService);
    }

    @Test
    void listOperationalOrdersReportsBothInvalidPageParameters() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/orders").with(authenticatedAs(Role.DISPATCHER))
                        .param("page", "-1").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].field").value("page"))
                .andExpect(jsonPath("$.details[1].field").value("size"));

        verifyNoInteractions(deliveryOrderService, dispatchService);
    }

    @Test
    void listOperationalOrdersRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "COURIER"})
    void listOperationalOrdersRejectsUnsupportedRole(Role role) throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/orders")
                        .with(authenticatedAs(role)).param("status", "PENDING").param("page", "1").param("size", "5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void autoAssignAllowsAdmin() throws Exception {
        DispatchAssignmentResponse response = new DispatchAssignmentResponse(11L, 7L, OrderStatus.ASSIGNED);
        when(dispatchService.autoAssignOrder(11L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/orders/11/auto-assign")
                        .with(authenticatedAs(Role.ADMIN)))
                .andExpect(status().isOk());

        verify(dispatchService).autoAssignOrder(11L);
    }

    @Test
    void autoAssignRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/orders/11/auto-assign"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void autoAssignRejectsUnsupportedRole() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/orders/11/auto-assign")
                        .with(authenticatedAs(Role.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void autoAssignReturnsNotFoundForMissingOrder() throws Exception {
        when(dispatchService.autoAssignOrder(404L)).thenThrow(new OrderNotFoundException(404L));

        mockMvc.perform(post("/api/v1/dispatch/orders/404/auto-assign")
                        .with(authenticatedAs(Role.DISPATCHER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void autoAssignReturnsConflictWhenNoCourierIsEligible() throws Exception {
        when(dispatchService.autoAssignOrder(11L)).thenThrow(new NoEligibleCourierException(11L));

        mockMvc.perform(post("/api/v1/dispatch/orders/11/auto-assign")
                        .with(authenticatedAs(Role.DISPATCHER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ELIGIBLE_COURIER"));
    }

    @Test
    void manualAssignReturnsSpecifiedCourierForDispatcher() throws Exception {
        DispatchAssignmentResponse response = new DispatchAssignmentResponse(11L, 7L, OrderStatus.ASSIGNED);
        when(dispatchService.manualAssignOrder(11L, 7L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(11))
                .andExpect(jsonPath("$.courierId").value(7))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        verify(dispatchService).manualAssignOrder(11L, 7L);
    }

    @Test
    void manualAssignAllowsAdmin() throws Exception {
        DispatchAssignmentResponse response = new DispatchAssignmentResponse(11L, 7L, OrderStatus.ASSIGNED);
        when(dispatchService.manualAssignOrder(11L, 7L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":7}"))
                .andExpect(status().isOk());

        verify(dispatchService).manualAssignOrder(11L, 7L);
    }

    @Test
    void manualAssignRejectsMissingCourierId() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void manualAssignRejectsNonPositiveCourierId() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void manualAssignRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":7}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void manualAssignRejectsUnsupportedRole() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void manualAssignReturnsNotFoundForMissingCourier() throws Exception {
        when(dispatchService.manualAssignOrder(11L, 404L)).thenThrow(new CourierNotFoundException(404L));

        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":404}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_FOUND"));
    }

    @Test
    void manualAssignReturnsConflictForUnavailableCourier() throws Exception {
        when(dispatchService.manualAssignOrder(11L, 7L))
                .thenThrow(new CourierNotEligibleForDispatchException(
                        7L,
                        CourierAvailabilityStatus.UNAVAILABLE));

        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_ELIGIBLE_FOR_DISPATCH"));
    }

    @Test
    void autoAssignReturnsConflictWhenConcurrentAssignmentWins() throws Exception {
        when(dispatchService.autoAssignOrder(11L)).thenThrow(new OrderAssignmentConflictException(11L));

        mockMvc.perform(post("/api/v1/dispatch/orders/11/auto-assign")
                        .with(authenticatedAs(Role.DISPATCHER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ASSIGNMENT_CONFLICT"));
    }

    @Test
    void manualAssignReturnsConflictWhenConcurrentAssignmentWins() throws Exception {
        when(dispatchService.manualAssignOrder(11L, 7L)).thenThrow(new OrderAssignmentConflictException(11L));

        mockMvc.perform(post("/api/v1/dispatch/orders/11/assign")
                        .with(authenticatedAs(Role.DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ASSIGNMENT_CONFLICT"));
    }

    private RequestPostProcessor authenticatedAs(Role role) {
        return jwt()
                .jwt(token -> token.claim("userId", 5L).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private OperationalOrderResponse operationalOrderResponse() {
        return new OperationalOrderResponse(
                11L,
                OrderStatus.ASSIGNED,
                3L,
                "customer@example.com",
                7L,
                "courier@example.com",
                "Ayse Courier",
                "Pickup",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Dropoff",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"),
                Instant.parse("2026-07-17T09:00:00Z"),
                Instant.parse("2026-07-17T09:05:00Z"));
    }

}
