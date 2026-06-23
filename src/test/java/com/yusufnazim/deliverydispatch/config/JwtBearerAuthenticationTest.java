package com.yusufnazim.deliverydispatch.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
class JwtBearerAuthenticationTest {

    private final MockMvc mockMvc;
    private final JwtTokenService jwtTokenService;

    @Autowired
    JwtBearerAuthenticationTest(MockMvc mockMvc, JwtTokenService jwtTokenService) {
        this.mockMvc = mockMvc;
        this.jwtTokenService = jwtTokenService;
    }

    @Test
    void protectedEndpointReturnsJsonForMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void protectedEndpointReturnsJsonForInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void protectedEndpointAcceptsValidBearerToken() throws Exception {
        String token = jwtTokenService.generateToken(user(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("protected"));
    }

    @Test
    void roleProtectedEndpointAcceptsMatchingRoleClaim() throws Exception {
        String token = jwtTokenService.generateToken(user(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/test/customer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("customer"));
    }

    @Test
    void roleProtectedEndpointReturnsJsonForDifferentRoleClaim() throws Exception {
        String token = jwtTokenService.generateToken(user(Role.COURIER));

        mockMvc.perform(get("/api/v1/test/customer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private User user(Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("customer@example.com");
        when(user.getRole()).thenReturn(role);
        return user;
    }

    @TestConfiguration
    static class ProtectedEndpointConfig {

        @Bean
        ProtectedTestController protectedTestController() {
            return new ProtectedTestController();
        }
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/test/protected")
        String protectedEndpoint() {
            return "protected";
        }

        @PreAuthorize("hasRole('CUSTOMER')")
        @GetMapping("/api/v1/test/customer")
        String customerEndpoint() {
            return "customer";
        }
    }
}
