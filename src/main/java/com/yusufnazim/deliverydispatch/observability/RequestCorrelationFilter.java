package com.yusufnazim.deliverydispatch.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String MDC_KEY = "requestId";
    private static final String REQUEST_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = (String) request.getAttribute(REQUEST_ATTRIBUTE);
        if (requestId == null) {
            requestId = resolveRequestId(request);
            request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        }

        String previousId = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        try {
            response.setHeader(HEADER_NAME, requestId);
            chain.doFilter(request, response);
        } finally {
            if (previousId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousId);
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
        // A container may reset the response while dispatching an error on the same thread.
        doFilterInternal(request, response, chain);
    }

    private String resolveRequestId(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(HEADER_NAME);
        if (values.hasMoreElements()) {
            String candidate = values.nextElement();
            if (!values.hasMoreElements() && SAFE_ID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }
}
