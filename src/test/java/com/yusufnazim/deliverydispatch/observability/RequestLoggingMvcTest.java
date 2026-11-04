package com.yusufnazim.deliverydispatch.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentConflictException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RequestLoggingMvcTest {

    private static final String PREFIX = "/api/v1/test/request-logging";
    private final MockMvc mockMvc;
    private final TestRestTemplate http;
    private final JwtTokenService tokens;
    private RequestLogCapture capture;

    @Autowired
    RequestLoggingMvcTest(MockMvc mockMvc, TestRestTemplate http, JwtTokenService tokens) {
        this.mockMvc = mockMvc;
        this.http = http;
        this.tokens = tokens;
    }

    @BeforeEach
    void captureLogs() {
        capture = new RequestLogCapture();
    }

    @AfterEach
    void stopCapture() {
        capture.close();
    }

    @Test
    void logsOnlyMetadataAndDoesNotConsumeOrLogBodiesHeadersQueriesOrPathValues() throws Exception {
        String token = token(Role.CUSTOMER);
        mockMvc.perform(post(PREFIX + "/orders/private-path-value")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-ID", "privacy-check")
                        .header("User-Agent", "agent-secret")
                        .header("Referer", "https://example.com/referrer-secret")
                        .cookie(new Cookie("session", "cookie-secret"))
                        .queryParam("access_token", "query-secret")
                        .contentType("text/plain").content("password-and-address-secret"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-ID", "privacy-check"))
                .andExpect(content().string("password-and-address-secret"));

        expectEntry("POST", PREFIX + "/orders/{privateId}", 201, "privacy-check");
        assertThat(capture.entries().getFirst().toString()).doesNotContain(token, "secret", "private-path-value");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logsSecurityRejectionsWithoutEchoingTheRawUrlOrToken(boolean invalidToken) throws Exception {
        var request = get("/api/v1/private-path-secret").queryParam("access_token", "query-secret")
                .header("X-Request-ID", "security-check");
        if (invalidToken) {
            request.header("Authorization", "Bearer invalid-token-secret");
        }
        mockMvc.perform(request).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        expectEntry("GET", "<unresolved>", 401, "security-check");
        assertThat(capture.entries().getFirst().toString()).doesNotContain("secret");
    }

    @Test
    void logsForbiddenRoleWithTheMatchedTemplate() throws Exception {
        mockMvc.perform(post(PREFIX + "/orders/private-value")
                        .header("Authorization", "Bearer " + token(Role.COURIER))
                        .header("X-Request-ID", "forbidden-check")
                        .contentType("text/plain").content("body-secret"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        expectEntry("POST", PREFIX + "/orders/{privateId}", 403, "forbidden-check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"password\":\"body-secret\",", "{\"email\":\"\",\"password\":\"body-secret\"}"})
    void logsParsingAndValidationErrorsWithoutTheSubmittedValues(String body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").header("X-Request-ID", "bad-request-check")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        expectEntry("POST", "/api/v1/auth/login", 400, "bad-request-check");
        assertThat(capture.entries().getFirst().toString()).doesNotContain("body-secret");
    }

    @ParameterizedTest
    @CsvSource({"missing,404", "conflict,409"})
    void logsDomainErrors(String outcome, int status) throws Exception {
        mockMvc.perform(get(PREFIX + "/outcome/" + outcome)
                        .header("Authorization", "Bearer " + token(Role.CUSTOMER))
                        .header("X-Request-ID", "domain-check"))
                .andExpect(status().is(status));
        expectEntry("GET", PREFIX + "/outcome/{outcome}", status, "domain-check");
    }

    @Test
    void logsEmptyResponses() throws Exception {
        mockMvc.perform(get(PREFIX + "/empty")
                        .header("Authorization", "Bearer " + token(Role.CUSTOMER))
                        .header("X-Request-ID", "idle-check"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        expectEntry("GET", PREFIX + "/empty", 204, "idle-check");
    }

    @Test
    void generatedIdsStayDistinctAcrossConsecutiveRequests() throws Exception {
        String first = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-ID");
        String second = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-ID");
        assertThat(first).isNotBlank().isNotEqualTo(second);
        assertThat(capture.entries()).hasSize(2);
        assertThat(capture.entries().get(0).message()).endsWith("requestId=" + first);
        assertThat(capture.entries().get(1).message()).endsWith("requestId=" + second);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void unsafeRequestIdsAreReplacedBeforeLogging() throws Exception {
        String id = mockMvc.perform(get("/actuator/health").header("X-Request-ID", "bad id-forged-secret"))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("X-Request-ID");
        assertThat(java.util.UUID.fromString(id)).isNotNull();
        expectEntry("GET", "/actuator/health", 200, id);
        assertThat(capture.entries().getFirst().toString()).doesNotContain("forged", "secret", "\r", "\n");
    }

    @ParameterizedTest
    @CsvSource({"fail,500", "send-error,503"})
    void realServletErrorDispatchLogsOnceWithTheFinalStatusAndOriginalRoute(String path, int status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(Role.CUSTOMER));
        headers.set("X-Request-ID", "live-error-check");
        ResponseEntity<String> response = http.exchange(PREFIX + "/" + path,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isEqualTo("live-error-check");
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                expectEntry("GET", PREFIX + "/" + path, status, "live-error-check"));
        assertThat(capture.entries().getFirst().toString()).doesNotContain("exception-secret", "error-message-secret");
    }

    private void expectEntry(String method, String route, int status, String id) {
        assertThat(capture.entries()).hasSize(1);
        var entry = capture.entries().getFirst();
        assertThat(entry.message()).startsWith("http_request method=" + method + " route=" + route
                        + " status=" + status + " durationMs=")
                .containsPattern("durationMs=\\d+ requestId=")
                .endsWith("requestId=" + id);
        assertThat(entry.context()).containsEntry("requestId", id);
        assertThat(entry.hasThrowable()).isFalse();
        assertThat(MDC.get("requestId")).isNull();
    }

    private String token(Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("email-secret@example.com");
        when(user.getRole()).thenReturn(role);
        return tokens.generateToken(user);
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        ProbeController requestLoggingProbeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @PreAuthorize("hasRole('CUSTOMER')")
        @PostMapping(PREFIX + "/orders/{privateId}")
        ResponseEntity<String> echo(@PathVariable String privateId, @RequestBody String body) {
            return ResponseEntity.status(201).body(body);
        }

        @GetMapping(PREFIX + "/outcome/{outcome}")
        void outcome(@PathVariable String outcome) {
            if (outcome.equals("missing")) {
                throw new OrderNotFoundException(1L);
            }
            throw new OrderAssignmentConflictException(1L);
        }

        @GetMapping(PREFIX + "/empty")
        ResponseEntity<Void> empty() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping(PREFIX + "/fail")
        void fail() {
            throw new IllegalStateException("exception-secret");
        }

        @GetMapping(PREFIX + "/send-error")
        void sendError(HttpServletResponse response) throws IOException {
            response.sendError(503, "error-message-secret");
        }
    }
}
