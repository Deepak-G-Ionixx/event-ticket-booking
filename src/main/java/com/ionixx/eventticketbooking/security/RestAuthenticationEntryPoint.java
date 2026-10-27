package com.ionixx.eventticketbooking.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter errorWriter;

    public RestAuthenticationEntryPoint(SecurityErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        Object failure = request.getAttribute(
                JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);

        String code;
        String message;
        String challenge;

        if (JwtAuthenticationFilter.FAILURE_EXPIRED.equals(failure)) {
            code = "TOKEN_EXPIRED";
            message = "Access token has expired. Please log in again";
            challenge = "Bearer error=\"invalid_token\", "
                    + "error_description=\"The access token expired\"";
        } else if (JwtAuthenticationFilter.FAILURE_INVALID.equals(failure)) {
            code = "INVALID_TOKEN";
            message = "Access token is invalid";
            challenge = "Bearer error=\"invalid_token\"";
        } else {
            code = "UNAUTHORIZED";
            message = "Authentication is required to access this resource";
            challenge = "Bearer";
        }

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);

        errorWriter.write(
                request, response, HttpStatus.UNAUTHORIZED, code, message);
    }
}