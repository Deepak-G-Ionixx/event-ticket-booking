package com.ionixx.eventticketbooking.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_FAILURE_ATTRIBUTE = "jwtAuthFailure";
    public static final String FAILURE_EXPIRED = "EXPIRED";
    public static final String FAILURE_INVALID = "INVALID";

    public static final String MDC_USER_ID = "userId";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null
                && header.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {

            String token = header.substring(BEARER_PREFIX.length()).trim();

            if (token.isEmpty()) {
                request.setAttribute(AUTH_FAILURE_ATTRIBUTE, FAILURE_INVALID);
            } else {
                authenticate(request, token);
            }
        }

        // The userId stays in the MDC until RequestIdFilter calls MDC.clear(), so the final
        // "HTTP request completed" log line also contains it.
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {

        try {
            UserPrincipal principal = jwtService.parseToken(token);

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.getAuthorities());
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            MDC.put(MDC_USER_ID, String.valueOf(principal.id()));

        } catch (ExpiredJwtException exception) {
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, FAILURE_EXPIRED);
            log.warn("JWT rejected: expired");

        } catch (RuntimeException exception) {
            // JwtException (bad signature, malformed, wrong issuer ...) or anything else
            // thrown while reading the token: either way the token cannot be trusted.
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, FAILURE_INVALID);
            // Only the exception type is logged, never the token itself.
            log.warn("JWT rejected: {}", exception.getClass().getSimpleName());
        }
    }
}