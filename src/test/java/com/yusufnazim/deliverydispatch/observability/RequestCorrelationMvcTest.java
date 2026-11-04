package com.yusufnazim.deliverydispatch.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentConflictException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
class RequestCorrelationMvcTest {

    private final MockMvc mockMvc;

    @Autowired
    RequestCorrelationMvcTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void controllerLogsAndResponseUseTheSameIdAndContextIsCleared() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ProbeController.class);
        List<Map<String, String>> contexts = new ArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                contexts.add(Map.copyOf(event.getMDCPropertyMap()));
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/api/v1/test/correlation").with(customer())
                            .header("X-Request-ID", "demo-request-123"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Request-ID", "demo-request-123"))
                    .andExpect(jsonPath("$.requestId").value("demo-request-123"));
            assertThat(contexts).hasSize(1);
            assertThat(contexts.getFirst()).containsEntry("requestId", "demo-request-123");
            assertThat(MDC.get("requestId")).isNull();

            String nextId = mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk()).andReturn().getResponse().getHeader("X-Request-ID");
            assertThat(nextId).isNotBlank().isNotEqualTo("demo-request-123");
            assertThat(MDC.get("requestId")).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void missingAndInvalidBearerTokensHaveCorrelationIdsBeforeSecurityRejectsThem() throws Exception {
        mockMvc.perform(get("/api/v1/test/correlation").header("X-Request-ID", "missing-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "missing-token"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(MDC.get("requestId")).isNull();

        mockMvc.perform(get("/api/v1/test/correlation")
                        .header("Authorization", "Bearer invalid-token")
                        .header("X-Request-ID", "invalid-token-request"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "invalid-token-request"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void forbiddenRoleHasACorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/test/correlation")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
                        .header("X-Request-ID", "forbidden-request"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Request-ID", "forbidden-request"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void parsingValidationAndDomainErrorsKeepTheId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{")
                        .header("X-Request-ID", "malformed-request"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-ID", "malformed-request"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(MDC.get("requestId")).isNull();

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}")
                        .header("X-Request-ID", "validation-request"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-ID", "validation-request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(MDC.get("requestId")).isNull();

        mockMvc.perform(get("/api/v1/test/correlation/missing").with(customer())
                        .header("X-Request-ID", "domain-request"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-ID", "domain-request"))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void publicEndpointsAlsoGenerateIds() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200'].headers['X-Request-ID'].schema.maxLength")
                        .value(64))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.responses['401'].headers['X-Request-ID']")
                        .exists());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void emptyResponsesAndConflictsKeepTheId() throws Exception {
        mockMvc.perform(get("/api/v1/test/correlation/empty").with(customer())
                        .header("X-Request-ID", "idle-request"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string("X-Request-ID", "idle-request"));
        mockMvc.perform(get("/api/v1/test/correlation/conflict").with(customer())
                        .header("X-Request-ID", "conflict-request"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ASSIGNMENT_CONFLICT"))
                .andExpect(header().string("X-Request-ID", "conflict-request"));
        assertThat(MDC.get("requestId")).isNull();
    }

    private RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        ProbeController correlationProbeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @PreAuthorize("hasRole('CUSTOMER')")
        @GetMapping("/api/v1/test/correlation")
        Map<String, String> correlated() {
            LoggerFactory.getLogger(ProbeController.class).info("Correlation test probe");
            return Map.of("requestId", MDC.get("requestId"));
        }

        @GetMapping("/api/v1/test/correlation/missing")
        void missing() {
            throw new OrderNotFoundException(1L);
        }

        @GetMapping("/api/v1/test/correlation/empty")
        ResponseEntity<Void> empty() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/test/correlation/conflict")
        void conflict() {
            throw new OrderAssignmentConflictException(1L);
        }
    }
}
