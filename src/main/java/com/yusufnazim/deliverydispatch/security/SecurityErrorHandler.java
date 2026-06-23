package com.yusufnazim.deliverydispatch.security;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.exception.ApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final ApiErrorResponse UNAUTHORIZED_RESPONSE = new ApiErrorResponse(
            "AUTHENTICATION_REQUIRED",
            "Authentication is required");
    private static final ApiErrorResponse FORBIDDEN_RESPONSE = new ApiErrorResponse(
            "ACCESS_DENIED",
            "Access is denied");

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        writeError(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_RESPONSE);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        writeError(response, HttpStatus.FORBIDDEN, FORBIDDEN_RESPONSE);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorResponse apiErrorResponse) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiErrorResponse);
    }
}
