
package com.ionixx.eventticketbooking.filter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";


    private static final Logger log =
            LoggerFactory.getLogger(RequestIdFilter.class);

    private static final Pattern SAFE =
            Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String incomingId = request.getHeader(HEADER);

        String requestId =
                incomingId != null && SAFE.matcher(incomingId).matches()
                        ? incomingId
                        : "REQ-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        MDC.put(MDC_KEY, requestId);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());

        response.setHeader(HEADER, requestId);

        long start = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs =
                    (System.nanoTime() - start) / 1_000_000;

            Object eventLogId = request.getAttribute("eventLogId");
            if (eventLogId instanceof String value && !value.isBlank()) {
                MDC.put("eventLogId", value);
            }

            log.info(
                    "HTTP request completed: status={}, durationMs={}",
                    response.getStatus(),
                    durationMs
            );

            // Prevent request-specific values leaking into a reused server thread.
            MDC.clear();
        }
    }
}
