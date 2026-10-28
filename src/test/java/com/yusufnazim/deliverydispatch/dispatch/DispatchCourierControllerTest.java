package com.yusufnazim.deliverydispatch.dispatch;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.courier.CourierService;
import com.yusufnazim.deliverydispatch.courier.dto.OperationalCourierResponse;
import com.yusufnazim.deliverydispatch.dto.PageResponse;
import com.yusufnazim.deliverydispatch.exception.GlobalExceptionHandler;
import com.yusufnazim.deliverydispatch.security.SecurityErrorHandler;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.CourierVehicleType;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
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
        when(courierService.listOperationalCouriers(null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(operationalCourierResponse()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/dispatch/couriers")
                        .with(authenticatedAs(Role.DISPATCHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].email").value("courier@example.com"))
                .andExpect(jsonPath("$.items[0].displayName").value("Ayse Courier"))
                .andExpect(jsonPath("$.items[0].phoneNumber").value("+905551112233"))
                .andExpect(jsonPath("$.items[0].vehicleType").value("MOTORBIKE"))
                .andExpect(jsonPath("$.items[0].availabilityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.items[0].latitude").value(41.008200))
                .andExpect(jsonPath("$.items[0].longitude").value(28.978400))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-07-17T09:00:00Z"))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-07-17T09:05:00Z"))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(courierService).listOperationalCouriers(null, 0, 20);
    }

    @Test
    void listOperationalCouriersAllowsAdmin() throws Exception {
        when(courierService.listOperationalCouriers(null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/couriers")
                        .with(authenticatedAs(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
                        """, JsonCompareMode.STRICT));

        verify(courierService).listOperationalCouriers(null, 0, 20);
    }

    @ParameterizedTest
    @EnumSource(CourierAvailabilityStatus.class)
    void listOperationalCouriersAcceptsEachAvailabilityFilter(CourierAvailabilityStatus availability) throws Exception {
        when(courierService.listOperationalCouriers(availability, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.DISPATCHER))
                        .param("availability", availability.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(courierService).listOperationalCouriers(availability, 0, 20);
    }

    @Test
    void listOperationalCouriersAllowsAdminToFilterAndPageResults() throws Exception {
        when(courierService.listOperationalCouriers(CourierAvailabilityStatus.AVAILABLE, 2, 10))
                .thenReturn(new PageResponse<>(List.of(operationalCourierResponse()), 2, 10, 21, 3));

        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.ADMIN))
                        .param("availability", "AVAILABLE").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].availabilityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(courierService).listOperationalCouriers(CourierAvailabilityStatus.AVAILABLE, 2, 10);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "21474836,100"})
    void listOperationalCouriersAcceptsBoundsWithEmptyAvailabilityAsNoFilter(int page, int size) throws Exception {
        when(courierService.listOperationalCouriers(null, page, size))
                .thenReturn(new PageResponse<>(List.of(), page, size, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.DISPATCHER))
                        .param("availability", "").param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size));

        verify(courierService).listOperationalCouriers(null, page, size);
    }

    @ParameterizedTest
    @CsvSource({"page,3,3,20", "size,10,0,10"})
    void listOperationalCouriersDefaultsEachOmittedParameterIndependently(String parameter, String value, int page, int size)
            throws Exception {
        when(courierService.listOperationalCouriers(null, page, size))
                .thenReturn(new PageResponse<>(List.of(), page, size, 0, 0));

        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.DISPATCHER)).param(parameter, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size));

        verify(courierService).listOperationalCouriers(null, page, size);
    }

    @Test
    void listOperationalCouriersKeepsFilteredTotalsBeyondTheLastPage() throws Exception {
        when(courierService.listOperationalCouriers(CourierAvailabilityStatus.AVAILABLE, 3, 2))
                .thenReturn(new PageResponse<>(List.of(), 3, 2, 5, 3));

        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.DISPATCHER))
                        .param("availability", "AVAILABLE").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items":[],"page":3,"size":2,"totalElements":5,"totalPages":3}
                        """, JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @CsvSource({
            "availability,UNKNOWN", "availability,available", "availability,1",
            "page,abc", "size,abc", "page,1.5", "size,1.5", "page,2147483648", "size,2147483648"
    })
    void listOperationalCouriersRejectsInvalidEnumAndNonIntegerParameters(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.DISPATCHER)).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST_PARAMETER","message":"Request parameter is missing or invalid","details":[]}
                        """, JsonCompareMode.STRICT));

        verifyNoInteractions(courierService);
    }

    @ParameterizedTest
    @CsvSource({
            "page,-1,must be greater than or equal to 0",
            "page,21474837,must be less than or equal to 21474836",
            "size,0,must be greater than or equal to 1",
            "size,-1,must be greater than or equal to 1",
            "size,101,must be less than or equal to 100"
    })
    void listOperationalCouriersRejectsOutOfRangeParameters(String parameter, String value, String message) throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.DISPATCHER)).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value(parameter))
                .andExpect(jsonPath("$.details[0].message").value(message));

        verifyNoInteractions(courierService);
    }

    @Test
    void listOperationalCouriersReportsBothInvalidPageParameters() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers").with(authenticatedAs(Role.DISPATCHER))
                        .param("page", "-1").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].field").value("page"))
                .andExpect(jsonPath("$.details[1].field").value("size"));

        verifyNoInteractions(courierService);
    }

    @Test
    void listOperationalCouriersRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(courierService);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "COURIER"})
    void listOperationalCouriersRejectsUnsupportedRole(Role role) throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers")
                        .with(authenticatedAs(role)).param("availability", "AVAILABLE").param("page", "1").param("size", "5"))
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
