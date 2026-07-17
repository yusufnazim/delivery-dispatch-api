package com.yusufnazim.deliverydispatch.dispatch;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yusufnazim.deliverydispatch.config.SecurityConfig;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.dispatch.dto.DispatchAssignmentResponse;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierNotEligibleForDispatchException;
import com.yusufnazim.deliverydispatch.dispatch.exception.NoEligibleCourierException;
import com.yusufnazim.deliverydispatch.exception.GlobalExceptionHandler;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.security.SecurityErrorHandler;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
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

@WebMvcTest(DispatchController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
class DispatchControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private DispatchService dispatchService;

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

    private RequestPostProcessor authenticatedAs(Role role) {
        return jwt()
                .jwt(token -> token.claim("userId", 5L).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

}
