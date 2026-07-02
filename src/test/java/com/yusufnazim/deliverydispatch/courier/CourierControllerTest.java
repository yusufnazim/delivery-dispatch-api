package com.yusufnazim.deliverydispatch.courier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierAvailabilityRequest;
import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CourierControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JwtTokenService jwtTokenService;

    @MockitoBean
    private CourierService courierService;

    @Autowired
    CourierControllerTest(MockMvc mockMvc, ObjectMapper objectMapper, JwtTokenService jwtTokenService) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jwtTokenService = jwtTokenService;
    }

    @Test
    void updateAvailabilityReturnsUpdatedStatusForCourier() throws Exception {
        UpdateCourierAvailabilityRequest request =
                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.AVAILABLE);
        when(courierService.updateAvailability(7L, CourierAvailabilityStatus.AVAILABLE))
                .thenReturn(new CourierAvailabilityResponse(7L, CourierAvailabilityStatus.AVAILABLE));

        mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .header("Authorization", bearerToken(7L, Role.COURIER))
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
                        .header("Authorization", bearerToken(7L, Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
                        .header("Authorization", bearerToken(9L, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(courierService);
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
