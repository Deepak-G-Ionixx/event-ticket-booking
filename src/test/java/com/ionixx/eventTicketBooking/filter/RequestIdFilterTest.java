package com.ionixx.eventticketbooking.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private MockHttpServletRequest request(String requestIdHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
        if (requestIdHeader != null) {
            request.addHeader(RequestIdFilter.HEADER, requestIdHeader);
        }
        return request;
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(null), response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .startsWith("REQ-")
                .matches("REQ-[0-9a-f]{32}");
    }

    @Test
    void reusesSafeIncomingRequestId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("client-req-12345"), response,
                (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isEqualTo("client-req-12345");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",                      // fewer than 8 characters
            "has spaces in it!",          // unsafe characters
            "line\tbreak-12345",          // control character
            "this-id-is-way-too-long-this-id-is-way-too-long-this-id-is-way-too-long"
    })
    void replacesUnsafeIncomingRequestId(String unsafe) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(unsafe), response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo(unsafe)
                .startsWith("REQ-");
    }

    @Test
    void putsRequestDetailsInMdcWhileTheChainRuns() throws Exception {
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            requestId.set(MDC.get(RequestIdFilter.MDC_KEY));
            method.set(MDC.get("method"));
            path.set(MDC.get("path"));
        };

        filter.doFilter(request("client-req-98765"), new MockHttpServletResponse(), chain);

        assertThat(requestId.get()).isEqualTo("client-req-98765");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/api/events");
    }

    @Test
    void clearsMdcAfterTheRequestCompletes() throws Exception {
        filter.doFilter(request(null), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("path")).isNull();
    }

    @Test
    void clearsMdcEvenWhenTheChainFails() {
        FilterChain failing = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(
                request(null), new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void handlesEventLogIdAttributeOfEveryShape() throws Exception {
        for (Object attribute : new Object[] {"ETB0001", "   ", 42, null}) {
            MockHttpServletRequest request = request(null);
            if (attribute != null) {
                request.setAttribute("eventLogId", attribute);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> { });

            assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
            assertThat(MDC.get("eventLogId")).isNull();
        }
    }
}