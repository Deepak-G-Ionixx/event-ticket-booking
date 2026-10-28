package com.ionixx.eventticketbooking.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final SecurityErrorResponseWriter errorWriter;

    public RestAccessDeniedHandler(SecurityErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        // userId is already in the MDC, set by JwtAuthenticationFilter.
        log.warn("Access denied: method={}, path={}",
                request.getMethod(), request.getRequestURI());

        errorWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to perform this action");
    }
}
