package com.yusufnazim.deliverydispatch.courier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierAvailabilityRequest;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierLocationRequest;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.InvalidOrderStatusTransitionException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.exception.GlobalExceptionHandler;
import com.yusufnazim.deliverydispatch.security.SecurityErrorHandler;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CourierController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
class CourierControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private CourierService courierService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    CourierControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void updateAvailabilityReturnsUpdatedStatusForCourier() throws Exception {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.AVAILABLE);
        when(courierService.updateAvailability(7L, CourierAvailabilityStatus.AVAILABLE))
                .thenReturn(new CourierAvailabilityResponse(7L, CourierAvailabilityStatus.AVAILABLE));

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(7))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        verify(courierService).updateAvailability(7L, CourierAvailabilityStatus.AVAILABLE);
    }

    @Test
    void updateAvailabilityRejectsSystemManagedStatus() throws Exception {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.ON_DELIVERY);

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void updateAvailabilityRejectsMissingStatus() throws Exception {
        String invalidRequest = "{}";

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void updateAvailabilityRejectsMissingBearerToken() throws Exception {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void updateAvailabilityRejectsNonCourierRole() throws Exception {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(9L, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void updateLocationReturnsUpdatedCoordinatesForCourier() throws Exception {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("41.008200"),
                new BigDecimal("28.978400"));
        when(courierService.updateLocation(7L, request.latitude(), request.longitude()))
                .thenReturn(new CourierLocationResponse(7L, request.latitude(), request.longitude()));

        mockMvc.perform(patch("/api/v1/couriers/me/location")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(7))
                .andExpect(jsonPath("$.latitude").value(41.008200))
                .andExpect(jsonPath("$.longitude").value(28.978400));

        verify(courierService).updateLocation(7L, request.latitude(), request.longitude());
    }

    @Test
    void updateLocationRejectsOutOfRangeCoordinates() throws Exception {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("91.000000"),
                new BigDecimal("28.978400"));

        mockMvc.perform(patch("/api/v1/couriers/me/location")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void updateLocationRejectsMissingCoordinates() throws Exception {
        String invalidRequest = """
                {
                  "latitude": 41.008200
                }
                """;

        mockMvc.perform(patch("/api/v1/couriers/me/location")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void updateLocationRejectsMissingBearerToken() throws Exception {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("41.008200"),
                new BigDecimal("28.978400"));

        mockMvc.perform(patch("/api/v1/couriers/me/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void updateLocationRejectsNonCourierRole() throws Exception {
        UpdateCourierLocationRequest request = new UpdateCourierLocationRequest(
                new BigDecimal("41.008200"),
                new BigDecimal("28.978400"));

        mockMvc.perform(patch("/api/v1/couriers/me/location")
                        .with(authenticatedAs(9L, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void pickupOrderReturnsPickedUpOrderForCourier() throws Exception {
        when(courierService.pickupOrder(7L, 11L)).thenReturn(orderResponse(11L, OrderStatus.PICKED_UP));

        mockMvc.perform(post("/api/v1/couriers/me/orders/11/pickup")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.pickupAddress").value("Istiklal Cd. No:1, Beyoglu"));

        verify(courierService).pickupOrder(7L, 11L);
    }

    @Test
    void pickupOrderReturnsNotFoundForMissingOrWrongCourierOrder() throws Exception {
        when(courierService.pickupOrder(7L, 11L)).thenThrow(new OrderNotFoundException(11L));

        mockMvc.perform(post("/api/v1/couriers/me/orders/11/pickup")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        verify(courierService).pickupOrder(7L, 11L);
    }

    @Test
    void pickupOrderRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/couriers/me/orders/11/pickup"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void pickupOrderRejectsNonCourierRole() throws Exception {
        mockMvc.perform(post("/api/v1/couriers/me/orders/11/pickup")
                        .with(authenticatedAs(9L, Role.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void deliverOrderReturnsDeliveredOrderForCourier() throws Exception {
        when(courierService.deliverOrder(7L, 11L)).thenReturn(orderResponse(11L, OrderStatus.DELIVERED));

        mockMvc.perform(post("/api/v1/couriers/me/orders/11/deliver")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.dropoffAddress").value("Bagdat Cd. No:10, Kadikoy"));

        verify(courierService).deliverOrder(7L, 11L);
    }

    @Test
    void deliverOrderReturnsNotFoundForMissingOrWrongCourierOrder() throws Exception {
        when(courierService.deliverOrder(7L, 11L)).thenThrow(new OrderNotFoundException(11L));

        mockMvc.perform(post("/api/v1/couriers/me/orders/11/deliver")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        verify(courierService).deliverOrder(7L, 11L);
    }

    @Test
    void deliverOrderReturnsConflictForInvalidStatus() throws Exception {
        when(courierService.deliverOrder(7L, 11L))
                .thenThrow(new InvalidOrderStatusTransitionException(OrderStatus.ASSIGNED, OrderStatus.DELIVERED));

        mockMvc.perform(post("/api/v1/couriers/me/orders/11/deliver")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));

        verify(courierService).deliverOrder(7L, 11L);
    }

    @Test
    void deliverOrderRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/couriers/me/orders/11/deliver"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void deliverOrderRejectsNonCourierRole() throws Exception {
        mockMvc.perform(post("/api/v1/couriers/me/orders/11/deliver")
                        .with(authenticatedAs(9L, Role.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(courierService);
    }

    private RequestPostProcessor authenticatedAs(Long userId, Role role) {
        return jwt()
                .jwt(token -> token.claim("userId", userId).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private DeliveryOrderResponse orderResponse(Long orderId, OrderStatus status) {
        return new DeliveryOrderResponse(
                orderId,
                status,
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"),
                Instant.parse("2026-06-30T10:00:00Z"),
                Instant.parse("2026-06-30T10:05:00Z"));
    }
}
