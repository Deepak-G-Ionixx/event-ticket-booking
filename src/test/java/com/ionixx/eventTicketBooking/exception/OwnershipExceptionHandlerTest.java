
        package com.ionixx.eventticketbooking.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ionixx.eventticketbooking.dto.ApiErrorResponse;
import com.ionixx.eventticketbooking.filter.RequestIdFilter;

class OwnershipExceptionHandlerTest {

    private static final String REQUEST_ID = "REQ-owner-1234";

    private MockHttpServletRequest request;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/events/1");
        handler = new GlobalExceptionHandler(request);

        // Set a request ID so the error response can be checked.
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
    }

    @AfterEach
    void clearMdc() {
        // Avoid leaking MDC values into other tests.
        MDC.clear();
    }

    @Test
    void resourceAccessDeniedIsAForbiddenResponse() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleResourceAccessDenied(
                        new ResourceAccessDeniedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ApiErrorResponse body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(403);
        assertThat(body.code()).isEqualTo("RESOURCE_ACCESS_DENIED");
        assertThat(body.message())
                .isEqualTo("You are not allowed to access this resource");
        assertThat(body.path()).isEqualTo("/api/events/1");
        assertThat(body.requestId()).isEqualTo(REQUEST_ID);

        assertThat(response.getHeaders().getFirst(RequestIdFilter.HEADER))
                .isEqualTo(REQUEST_ID);
    }
}
