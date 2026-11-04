package com.yusufnazim.deliverydispatch.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void generatesAnIdAndMakesItAvailableBeforeTheResponseIsCommitted() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            String id = MDC.get("requestId");
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
            response.flushBuffer();
        });
        assertThat(response.isCommitted()).isTrue();
        assertThat(MDC.get("requestId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "demo-123.A_b", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"})
    void acceptsASingleSafeClientId(String id) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-request-id", id);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo(id));
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
        assertThat(response.getHeaders("X-Request-ID")).containsExactly(id);
        assertThat(MDC.get("requestId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " leading", "trailing ", "bad id", "bad\r\nforged: value",
            "id\tvalue", "a,b", "a/b", "a:b", "\u00e9", "-bad", "_bad", ".bad",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdefx"})
    void replacesUnsafeOrOversizedIdsWithoutEchoingThem(String unsafe) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", unsafe);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isNotEqualTo(unsafe);
            assertThat(UUID.fromString(MDC.get("requestId"))).isNotNull();
        });
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
    }

    @Test
    void replacesDuplicateHeadersEvenWhenBothValuesAreSafe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "first");
        request.addHeader("X-Request-ID", "second");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
    }

    @Test
    void restoresTheOuterContextWithoutRemovingOtherMdcEntries() throws Exception {
        MDC.put("requestId", "outer");
        MDC.put("other", "preserved");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "inner");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("inner");
            assertThat(MDC.get("other")).isEqualTo("preserved");
        });
        assertThat(MDC.get("requestId")).isEqualTo("outer");
        assertThat(MDC.get("other")).isEqualTo("preserved");
    }

    @Test
    void cleansUpAfterAnUnhandledFailureAndDoesNotReuseItsId() throws Exception {
        MockHttpServletResponse failed = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), failed, (req, res) -> {
            throw new ServletException("test failure");
        })).isInstanceOf(ServletException.class);
        assertThat(MDC.get("requestId")).isNull();

        MockHttpServletResponse next = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), next, (req, res) ->
                assertThat(MDC.get("requestId")).isEqualTo(next.getHeader("X-Request-ID")));
        assertThat(next.getHeader("X-Request-ID")).isNotEqualTo(failed.getHeader("X-Request-ID"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void cleansUpAfterAnIoFailure() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new IOException("disconnected"); }))
                .isInstanceOf(IOException.class);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void reusesTheOriginalIdDuringAnErrorRedispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        String id = response.getHeader("X-Request-ID");
        response.reset();
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/failed");
        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo(id));
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void reappliesTheHeaderDuringANestedErrorDispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            String id = MDC.get("requestId");
            response.reset();
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/failed");
            filter.doFilter(request, response, (nestedReq, nestedRes) -> {
                assertThat(MDC.get("requestId")).isEqualTo(id);
                assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
            });
            assertThat(MDC.get("requestId")).isEqualTo(id);
        });
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void keepsConcurrentRequestsIsolated() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentRequest("first", barrier));
            var second = executor.submit(() -> concurrentRequest("second", barrier));
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("second");
        }
    }

    private String concurrentRequest(String id, CyclicBarrier barrier) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", id);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new ServletException(ex);
            }
            assertThat(MDC.get("requestId")).isEqualTo(id);
        });
        assertThat(MDC.get("requestId")).isNull();
        return response.getHeader("X-Request-ID");
    }
}
