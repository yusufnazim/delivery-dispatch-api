package com.yusufnazim.deliverydispatch.observability;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".state";
    private static final Set<String> METHODS = Set.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "CONNECT");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RequestLogState state = (RequestLogState) request.getAttribute(REQUEST_ATTRIBUTE);
        if (state == null) {
            state = new RequestLogState(METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER");
            request.setAttribute(REQUEST_ATTRIBUTE, state);
        }

        boolean errorDispatch = request.getDispatcherType() == DispatcherType.ERROR;
        boolean completed = false;
        try {
            chain.doFilter(request, new ErrorTrackingResponse(response, request, state));
            completed = true;
        } finally {
            if (!errorDispatch) {
                state.captureRoute(request);
            }
            // Unhandled failures and sendError complete in the container's ERROR dispatch.
            if (completed && (errorDispatch || !state.errorRequested) && !request.isAsyncStarted()) {
                state.write(response.getStatus());
            }
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    private static final class RequestLogState {
        private final long startedNanos = System.nanoTime();
        private final String method;
        private String route = "<unresolved>";
        private boolean errorRequested;
        private boolean logged;

        private RequestLogState(String method) {
            this.method = method;
        }

        private void captureRoute(HttpServletRequest request) {
            if (request.getDispatcherType() != DispatcherType.ERROR
                    && request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String pattern) {
                route = pattern;
            }
        }

        private void write(int status) {
            if (!logged) {
                logged = true;
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
                log.info("http_request method={} route={} status={} durationMs={} requestId={}",
                        method, route, status, durationMs, MDC.get(RequestCorrelationFilter.MDC_KEY));
            }
        }
    }

    private static final class ErrorTrackingResponse extends HttpServletResponseWrapper {
        private final HttpServletRequest request;
        private final RequestLogState state;

        private ErrorTrackingResponse(HttpServletResponse response, HttpServletRequest request, RequestLogState state) {
            super(response);
            this.request = request;
            this.state = state;
        }

        @Override
        public void sendError(int status) throws IOException {
            state.captureRoute(request);
            state.errorRequested = true;
            super.sendError(status);
        }

        @Override
        public void sendError(int status, String message) throws IOException {
            state.captureRoute(request);
            state.errorRequested = true;
            super.sendError(status, message);
        }
    }
}
