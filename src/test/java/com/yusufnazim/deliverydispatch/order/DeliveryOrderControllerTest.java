package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.exception.GlobalExceptionHandler;
import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.OrderCancellationNotAllowedException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.security.SecurityErrorHandler;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventType;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.timeline.dto.DeliveryEventResponse;
import com.yusufnazim.deliverydispatch.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(DeliveryOrderController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
class DeliveryOrderControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private DeliveryOrderService deliveryOrderService;

    @MockitoBean
    private DeliveryTimelineService deliveryTimelineService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    DeliveryOrderControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void createOrderReturnsCreatedOrderForCustomer() throws Exception {
        CreateDeliveryOrderRequest request = validRequest();
        DeliveryOrderResponse response = new DeliveryOrderResponse(
                11L,
                OrderStatus.PENDING,
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"),
                Instant.parse("2026-06-29T10:00:00Z"),
                Instant.parse("2026-06-29T10:00:00Z"));
        when(deliveryOrderService.createOrder(anyLong(), any(CreateDeliveryOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .with(authenticatedAs(7L, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pickupAddress").value("Istiklal Cd. No:1, Beyoglu"))
                .andExpect(jsonPath("$.dropoffAddress").value("Bagdat Cd. No:10, Kadikoy"));

        ArgumentCaptor<CreateDeliveryOrderRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateDeliveryOrderRequest.class);
        ArgumentCaptor<Long> customerIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(deliveryOrderService).createOrder(customerIdCaptor.capture(), requestCaptor.capture());
        assertThat(customerIdCaptor.getValue()).isEqualTo(7L);
        assertThat(requestCaptor.getValue().pickupAddress()).isEqualTo("Istiklal Cd. No:1, Beyoglu");
        assertThat(requestCaptor.getValue().pickupLatitude()).isEqualByComparingTo("41.036900");
        assertThat(requestCaptor.getValue().pickupLongitude()).isEqualByComparingTo("28.985000");
        assertThat(requestCaptor.getValue().dropoffAddress()).isEqualTo("Bagdat Cd. No:10, Kadikoy");
        assertThat(requestCaptor.getValue().dropoffLatitude()).isEqualByComparingTo("40.970000");
        assertThat(requestCaptor.getValue().dropoffLongitude()).isEqualByComparingTo("29.057000");
    }

    @Test
    void createOrderRejectsInvalidRequest() throws Exception {
        String invalidRequest = """
                {
                  "pickupAddress": "",
                  "pickupLatitude": 91.000000,
                  "pickupLongitude": 28.985000,
                  "dropoffAddress": "Bagdat Cd. No:10, Kadikoy",
                  "dropoffLatitude": 40.970000,
                  "dropoffLongitude": 29.057000
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .with(authenticatedAs(7L, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void createOrderRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void createOrderRejectsNonCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(authenticatedAs(9L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void listOrdersReturnsCustomerOrders() throws Exception {
        when(deliveryOrderService.listCustomerOrders(7L))
                .thenReturn(List.of(
                        orderResponse(12L, "Pickup B"),
                        orderResponse(11L, "Pickup A")));

        mockMvc.perform(get("/api/v1/orders")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].pickupAddress").value("Pickup B"))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].pickupAddress").value("Pickup A"));

        verify(deliveryOrderService).listCustomerOrders(7L);
    }

    @Test
    void getOrderReturnsOwnedOrder() throws Exception {
        when(deliveryOrderService.getCustomerOrder(7L, 11L)).thenReturn(orderResponse(11L, "Pickup A"));

        mockMvc.perform(get("/api/v1/orders/11")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pickupAddress").value("Pickup A"));

        verify(deliveryOrderService).getCustomerOrder(7L, 11L);
    }

    @Test
    void getOrderTimelineReturnsOwnedOrderEvents() throws Exception {
        when(deliveryTimelineService.getCustomerOrderTimeline(7L, 11L))
                .thenReturn(List.of(
                        new DeliveryEventResponse(
                                DeliveryEventType.ORDER_CREATED,
                                "Order created",
                                Instant.parse("2026-07-09T10:00:00Z")),
                        new DeliveryEventResponse(
                                DeliveryEventType.ORDER_PICKED_UP,
                                "Order picked up",
                                Instant.parse("2026-07-09T10:20:00Z"))));

        mockMvc.perform(get("/api/v1/orders/11/timeline")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("ORDER_CREATED"))
                .andExpect(jsonPath("$[0].description").value("Order created"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-07-09T10:00:00Z"))
                .andExpect(jsonPath("$[1].type").value("ORDER_PICKED_UP"))
                .andExpect(jsonPath("$[1].description").value("Order picked up"))
                .andExpect(jsonPath("$[1].createdAt").value("2026-07-09T10:20:00Z"));

        verify(deliveryTimelineService).getCustomerOrderTimeline(7L, 11L);
    }

    @Test
    void getOrderTimelineReturnsNotFoundForMissingOrUnownedOrder() throws Exception {
        when(deliveryTimelineService.getCustomerOrderTimeline(eq(7L), eq(404L)))
                .thenThrow(new OrderNotFoundException(404L));

        mockMvc.perform(get("/api/v1/orders/404/timeline")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void getOrderReturnsNotFoundForMissingOrUnownedOrder() throws Exception {
        when(deliveryOrderService.getCustomerOrder(eq(7L), eq(404L)))
                .thenThrow(new OrderNotFoundException(404L));

        mockMvc.perform(get("/api/v1/orders/404")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelOrderReturnsCancelledOrderForCustomer() throws Exception {
        DeliveryOrderResponse response = orderResponse(11L, "Pickup A", OrderStatus.CANCELLED);
        when(deliveryOrderService.cancelCustomerOrder(7L, 11L)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/orders/11")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.pickupAddress").value("Pickup A"));

        verify(deliveryOrderService).cancelCustomerOrder(7L, 11L);
    }

    @Test
    void cancelOrderReturnsNotFoundForMissingOrUnownedOrder() throws Exception {
        when(deliveryOrderService.cancelCustomerOrder(eq(7L), eq(404L)))
                .thenThrow(new OrderNotFoundException(404L));

        mockMvc.perform(delete("/api/v1/orders/404")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelOrderReturnsConflictForNonCancellableOrder() throws Exception {
        when(deliveryOrderService.cancelCustomerOrder(eq(7L), eq(11L)))
                .thenThrow(new OrderCancellationNotAllowedException(OrderStatus.ASSIGNED));

        mockMvc.perform(delete("/api/v1/orders/11")
                        .with(authenticatedAs(7L, Role.CUSTOMER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCELLATION_NOT_ALLOWED"));
    }

    @Test
    void cancelOrderRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/11"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void cancelOrderRejectsNonCustomerRole() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/11")
                        .with(authenticatedAs(9L, Role.DISPATCHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void listOrdersRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void getOrderTimelineRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/orders/11/timeline"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(deliveryTimelineService);
    }

    @Test
    void getOrderRejectsNonCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/orders/11")
                        .with(authenticatedAs(9L, Role.DISPATCHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(deliveryOrderService);
    }

    @Test
    void getOrderTimelineRejectsNonCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/orders/11/timeline")
                        .with(authenticatedAs(9L, Role.DISPATCHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(deliveryTimelineService);
    }

    private CreateDeliveryOrderRequest validRequest() {
        return new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }

    private DeliveryOrderResponse orderResponse(Long id, String pickupAddress) {
        return orderResponse(id, pickupAddress, OrderStatus.PENDING);
    }

    private DeliveryOrderResponse orderResponse(Long id, String pickupAddress, OrderStatus status) {
        return new DeliveryOrderResponse(
                id,
                status,
                pickupAddress,
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"),
                Instant.parse("2026-06-30T10:00:00Z"),
                Instant.parse("2026-06-30T10:05:00Z"));
    }

    private RequestPostProcessor authenticatedAs(Long userId, Role role) {
        return jwt()
                .jwt(token -> token.claim("userId", userId).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
