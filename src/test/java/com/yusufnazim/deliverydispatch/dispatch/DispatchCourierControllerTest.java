package com.yusufnazim.deliverydispatch.dispatch;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.courier.CourierService;
import com.yusufnazim.deliverydispatch.courier.dto.OperationalCourierResponse;
import com.yusufnazim.deliverydispatch.exception.GlobalExceptionHandler;
import com.yusufnazim.deliverydispatch.security.SecurityErrorHandler;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.CourierVehicleType;
import com.yusufnazim.deliverydispatch.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(DispatchCourierController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
class DispatchCourierControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private CourierService courierService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    DispatchCourierControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void listOperationalCouriersReturnsCouriersForDispatcher() throws Exception {
        when(courierService.listOperationalCouriers()).thenReturn(List.of(operationalCourierResponse()));

        mockMvc.perform(get("/api/v1/dispatch/couriers")
                        .with(authenticatedAs(Role.DISPATCHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].email").value("courier@example.com"))
                .andExpect(jsonPath("$[0].displayName").value("Ayse Courier"))
                .andExpect(jsonPath("$[0].vehicleType").value("MOTORBIKE"))
                .andExpect(jsonPath("$[0].availabilityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].latitude").value(41.008200))
                .andExpect(jsonPath("$[0].longitude").value(28.978400));

        verify(courierService).listOperationalCouriers();
    }

    @Test
    void listOperationalCouriersAllowsAdmin() throws Exception {
        when(courierService.listOperationalCouriers()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/dispatch/couriers")
                        .with(authenticatedAs(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(courierService).listOperationalCouriers();
    }

    @Test
    void listOperationalCouriersRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(courierService);
    }

    @Test
    void listOperationalCouriersRejectsUnsupportedRole() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers")
                        .with(authenticatedAs(Role.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(courierService);
    }

    private RequestPostProcessor authenticatedAs(Role role) {
        return jwt()
                .jwt(token -> token.claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private OperationalCourierResponse operationalCourierResponse() {
        return new OperationalCourierResponse(
                7L,
                "courier@example.com",
                "Ayse Courier",
                "+905551112233",
                CourierVehicleType.MOTORBIKE,
                CourierAvailabilityStatus.AVAILABLE,
                new BigDecimal("41.008200"),
                new BigDecimal("28.978400"),
                Instant.parse("2026-07-17T09:00:00Z"),
                Instant.parse("2026-07-17T09:05:00Z"));
    }
}
