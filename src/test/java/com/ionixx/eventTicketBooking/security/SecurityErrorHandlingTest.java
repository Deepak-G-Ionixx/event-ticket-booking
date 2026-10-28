package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import com.ionixx.eventticketbooking.filter.RequestIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorHandlingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/events/1");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ------------------------------------------------------------------
    // SecurityErrorResponseWriter
    // ------------------------------------------------------------------

    @Test
    void writerProducesJsonErrorBody() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "REQ-writer-0001");
        SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter(JSON);

        writer.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "No access");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        JsonNode body = JSON.readTree(response.getContentAsString());
        assertThat(body.path("status").asInt()).isEqualTo(403);
        assertThat(body.path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.path("message").asText()).isEqualTo("No access");
        assertThat(body.path("path").asText()).isEqualTo("/api/events/1");
        assertThat(body.path("requestId").asText()).isEqualTo("REQ-writer-0001");
        assertThat(body.path("errors").isArray()).isTrue();
        assertThat(body.path("errors").isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------
    // RestAccessDeniedHandler
    // ------------------------------------------------------------------

    @Test
    void accessDeniedHandlerWrites403() throws Exception {
        SecurityErrorResponseWriter writer = mock(SecurityErrorResponseWriter.class);
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(writer);

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(writer).write(
                request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action");
    }

    // ------------------------------------------------------------------
    // RestAuthenticationEntryPoint
    // ------------------------------------------------------------------

    private RestAuthenticationEntryPoint entryPoint(SecurityErrorResponseWriter writer) {
        return new RestAuthenticationEntryPoint(writer);
    }

    @Test
    void entryPointReportsExpiredToken() throws Exception {
        SecurityErrorResponseWriter writer = mock(SecurityErrorResponseWriter.class);
        request.setAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE,
                JwtAuthenticationFilter.FAILURE_EXPIRED);

        entryPoint(writer).commence(request, response, new BadCredentialsException("x"));

        verify(writer).write(
                request, response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED",
                "Access token has expired. Please log in again");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .contains("invalid_token")
                .contains("The access token expired");
    }

    @Test
    void entryPointReportsInvalidToken() throws Exception {
        SecurityErrorResponseWriter writer = mock(SecurityErrorResponseWriter.class);
        request.setAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE,
                JwtAuthenticationFilter.FAILURE_INVALID);

        entryPoint(writer).commence(request, response, new BadCredentialsException("x"));

        verify(writer).write(
                request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "Access token is invalid");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer error=\"invalid_token\"");
    }

    @Test
    void entryPointReportsMissingAuthentication() throws Exception {
        SecurityErrorResponseWriter writer = mock(SecurityErrorResponseWriter.class);

        entryPoint(writer).commence(request, response, new BadCredentialsException("x"));

        verify(writer).write(
                eq(request), eq(response), eq(HttpStatus.UNAUTHORIZED), eq("UNAUTHORIZED"),
                eq("Authentication is required to access this resource"));
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }
}
