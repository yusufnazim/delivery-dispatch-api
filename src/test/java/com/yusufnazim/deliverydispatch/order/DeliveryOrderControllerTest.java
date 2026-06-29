package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryOrderControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JwtTokenService jwtTokenService;

    @MockitoBean
    private DeliveryOrderService deliveryOrderService;

    @Autowired
    DeliveryOrderControllerTest(MockMvc mockMvc, ObjectMapper objectMapper, JwtTokenService jwtTokenService) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jwtTokenService = jwtTokenService;
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
                        .header("Authorization", bearerToken(7L, Role.CUSTOMER))
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
                        .header("Authorization", bearerToken(7L, Role.CUSTOMER))
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
                        .header("Authorization", bearerToken(9L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(deliveryOrderService);
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

    private String bearerToken(Long userId, Role role) {
        return "Bearer " + jwtTokenService.generateToken(user(userId, role));
    }

    private User user(Long id, Role role) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("user%s@example.com".formatted(id));
        when(user.getRole()).thenReturn(role);
        return user;
    }
}
