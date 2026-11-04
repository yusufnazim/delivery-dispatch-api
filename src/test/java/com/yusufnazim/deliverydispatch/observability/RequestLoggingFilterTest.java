package com.yusufnazim.deliverydispatch.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

    private final RequestCorrelationFilter correlation = new RequestCorrelationFilter();
    private final RequestLoggingFilter logging = new RequestLoggingFilter();

    @Test
    void logsMetadataWithATemplateInsteadOfSensitiveRequestData() throws Exception {
        try (var capture = new RequestLogCapture()) {
            MockHttpServletRequest request = request("GET", "/orders/private-path-value", "safe-id");
            request.setQueryString("token=query-secret");
            request.setContent("body-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            request.addHeader("Authorization", "Bearer header-secret");
            request.addHeader("Cookie", "session=cookie-secret");
            request.addHeader("User-Agent", "agent-secret");
            request.addHeader("Referer", "https://example.com/referrer-secret");
            MockHttpServletResponse response = new MockHttpServletResponse();
            run(request, response, (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
                ((HttpServletResponse) res).setStatus(201);
                res.getWriter().write("response-secret");
            });

            assertThat(capture.entries()).hasSize(1);
            var entry = capture.entries().getFirst();
            assertThat(entry.message()).matches(
                    "http_request method=GET route=/orders/\\{id} status=201 durationMs=\\d+ requestId=safe-id");
            assertThat(entry.message()).doesNotContain("private-path-value", "secret");
            assertThat(entry.context()).containsEntry("requestId", "safe-id");
            assertThat(entry.hasThrowable()).isFalse();
            assertThat(response.getContentAsString()).isEqualTo("response-secret");
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test
    void unknownMethodsAndUnresolvedRoutesCannotInjectLogFields() throws Exception {
        try (var capture = new RequestLogCapture()) {
            MockHttpServletRequest request = request("EVIL\r\nforged=secret", "/private-secret", "safe-id");
            run(request, new MockHttpServletResponse(), (req, res) -> ((HttpServletResponse) res).setStatus(400));
            assertThat(capture.entries()).hasSize(1);
            assertThat(capture.entries().getFirst().message()).matches(
                    "http_request method=OTHER route=<unresolved> status=400 durationMs=\\d+ requestId=safe-id");
        }
    }

    @Test
    void unhandledFailureWaitsForTheFinalErrorDispatchAndKeepsTheOriginalRoute() throws Exception {
        try (var capture = new RequestLogCapture()) {
            MockHttpServletRequest request = request("GET", "/orders/private-id", "failure-id");
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThatThrownBy(() -> run(request, response, (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
                throw new ServletException("exception-secret");
            })).isInstanceOf(ServletException.class);
            assertThat(capture.entries()).isEmpty();
            assertThat(MDC.get("requestId")).isNull();

            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/orders/private-id");
            run(request, response, (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/error");
                ((HttpServletResponse) res).setStatus(500);
            });
            assertThat(capture.entries()).hasSize(1);
            assertThat(capture.entries().getFirst().message()).contains("route=/orders/{id} status=500")
                    .endsWith("requestId=failure-id").doesNotContain("exception-secret", "private-id", "/error");
            assertThat(capture.entries().getFirst().hasThrowable()).isFalse();
            run(request, response, (req, res) -> {});
            assertThat(capture.entries()).hasSize(1);
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sendErrorWaitsForRedispatchAndUsesItsFinalStatus(boolean withMessage) throws Exception {
        try (var capture = new RequestLogCapture()) {
            MockHttpServletRequest request = request("GET", "/private-secret", "send-error-id");
            MockHttpServletResponse response = new MockHttpServletResponse();
            run(request, response, (req, res) -> {
                if (withMessage) {
                    ((HttpServletResponse) res).sendError(404, "error-message-secret");
                } else {
                    ((HttpServletResponse) res).sendError(404);
                }
            });
            assertThat(capture.entries()).isEmpty();
            response.setCommitted(false);
            response.reset();
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/private-secret");
            run(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(401));
            assertThat(capture.entries()).hasSize(1);
            assertThat(capture.entries().getFirst().message()).contains("route=<unresolved> status=401")
                    .doesNotContain("secret");
        }
    }

    @Test
    void nestedErrorDispatchProducesOnlyOneEntry() throws Exception {
        try (var capture = new RequestLogCapture()) {
            MockHttpServletRequest request = request("GET", "/private-secret", "nested-id");
            MockHttpServletResponse response = new MockHttpServletResponse() {
                @Override
                public void sendError(int status) throws IOException {
                    request.setDispatcherType(DispatcherType.ERROR);
                    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/private-secret");
                    try {
                        run(request, this, (req, res) -> {
                            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/error");
                            ((HttpServletResponse) res).setStatus(status);
                        });
                    } catch (ServletException ex) {
                        throw new IOException(ex);
                    }
                }
            };
            run(request, response, (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
                ((HttpServletResponse) res).sendError(503);
            });
            assertThat(capture.entries()).hasSize(1);
            assertThat(capture.entries().getFirst().message()).contains("route=/orders/{id} status=503")
                    .endsWith("requestId=nested-id");
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test
    void concurrentRequestsLogTheirOwnIdsAndStatuses() throws Exception {
        try (var capture = new RequestLogCapture(); var executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            var first = executor.submit(() -> {
                concurrentRequest("first", 201, barrier);
                return null;
            });
            var second = executor.submit(() -> {
                concurrentRequest("second", 204, barrier);
                return null;
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(capture.entries()).hasSize(2);
            assertThat(capture.entries()).anySatisfy(entry -> {
                assertThat(entry.message()).contains("status=201").endsWith("requestId=first");
                assertThat(entry.context()).containsEntry("requestId", "first");
            }).anySatisfy(entry -> {
                assertThat(entry.message()).contains("status=204").endsWith("requestId=second");
                assertThat(entry.context()).containsEntry("requestId", "second");
            });
        }
    }

    private void concurrentRequest(String id, int status, CyclicBarrier barrier) throws Exception {
        run(request("GET", "/orders/private-value", id), new MockHttpServletResponse(), (req, res) -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new ServletException(ex);
            }
            ((HttpServletResponse) res).setStatus(status);
        });
        assertThat(MDC.get("requestId")).isNull();
    }

    private MockHttpServletRequest request(String method, String path, String id) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Request-ID", id);
        return request;
    }

    private void run(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        correlation.doFilter(request, response, (req, res) -> logging.doFilter(req, res, chain));
    }
}
