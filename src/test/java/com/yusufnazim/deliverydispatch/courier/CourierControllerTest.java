package com.yusufnazim.deliverydispatch.courier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierAvailabilityRequest;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierLocationRequest;
import com.yusufnazim.deliverydispatch.courier.exception.CourierAvailabilityChangeNotAllowedException;
import com.yusufnazim.deliverydispatch.courier.exception.CourierUpdateConflictException;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"ASSIGNED", "PICKED_UP"})
    void getCurrentOrderReturnsOnlyTheDeliveryResponseForCourier(OrderStatus orderStatus) throws Exception {
        DeliveryOrderResponse response = orderResponse(12L, orderStatus);
        when(courierService.getCurrentOrder(7L)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/couriers/me/orders/current")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.courier").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(courierService).getCurrentOrder(7L);
    }

    @Test
    void getCurrentOrderReturnsNoContentWhenIdle() throws Exception {
        when(courierService.getCurrentOrder(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/couriers/me/orders/current")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(courierService).getCurrentOrder(7L);
    }

    @Test
    void getCurrentOrderUsesTokenIdentityDespiteSuppliedCourierId() throws Exception {
        when(courierService.getCurrentOrder(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/couriers/me/orders/current")
                        .param("courierId", "8")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isNoContent());

        verify(courierService).getCurrentOrder(7L);
    }

    @Test
    void getCurrentOrderRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me/orders/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(courierService);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "DISPATCHER", "ADMIN"})
    void getCurrentOrderRejectsNonCourierRoles(Role role) throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me/orders/current")
                        .with(authenticatedAs(7L, role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(courierService);
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
    void updateAvailabilityReturnsConflictDuringActiveDelivery() throws Exception {
        when(courierService.updateAvailability(7L, CourierAvailabilityStatus.UNAVAILABLE))
                .thenThrow(new CourierAvailabilityChangeNotAllowedException(7L));

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNAVAILABLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_AVAILABILITY_CHANGE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(
                        "Courier availability cannot change during an active delivery: 7"));
    }

    @Test
    void updateAvailabilityReturnsConflictForConcurrentWrite() throws Exception {
        when(courierService.updateAvailability(7L, CourierAvailabilityStatus.AVAILABLE))
                .thenThrow(new CourierUpdateConflictException(7L, new RuntimeException("internal database details")));

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_UPDATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Courier or delivery state changed concurrently; reload before retrying: 7"))
                .andExpect(jsonPath("$.cause").doesNotExist());
    }

    @Test
    void updateLocationReturnsConflictForConcurrentWrite() throws Exception {
        when(courierService.updateLocation(7L, BigDecimal.ONE, BigDecimal.TEN))
                .thenThrow(new CourierUpdateConflictException(7L, null));

        mockMvc.perform(patch("/api/v1/couriers/me/location")
                        .with(authenticatedAs(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":1,\"longitude\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_UPDATE_CONFLICT"));
    }

    @Test
    void deliverOrderReturnsConflictForConcurrentWrite() throws Exception {
        when(courierService.deliverOrder(7L, 11L)).thenThrow(new CourierUpdateConflictException(7L, null));

        mockMvc.perform(post("/api/v1/couriers/me/orders/11/deliver")
                        .with(authenticatedAs(7L, Role.COURIER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_UPDATE_CONFLICT"));
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
