package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ionixx.eventticketbooking.entity.Role;

import jakarta.servlet.FilterChain;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijkl";
    private static final String ISSUER = "event-ticket-booking";

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, Duration.ofMinutes(5), ISSUER));

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService);

    private final UserPrincipal customer =
            new UserPrincipal(42, "cust@example.com", "hash", Role.CUSTOMER);

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private MockHttpServletRequest requestWithAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/1");
        if (value != null) {
            request.addHeader("Authorization", value);
        }
        return request;
    }

    @Test
    void validTokenAuthenticatesTheRequestAndPutsUserIdInMdc() throws Exception {
        String token = jwtService.generateToken(customer);

        AtomicReference<Authentication> seenInChain = new AtomicReference<>();
        AtomicReference<String> mdcInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seenInChain.set(SecurityContextHolder.getContext().getAuthentication());
            mdcInChain.set(MDC.get("userId"));
        };

        filter.doFilter(
                requestWithAuthorization("Bearer " + token),
                new MockHttpServletResponse(),
                chain);

        Authentication authentication = seenInChain.get();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(((UserPrincipal) authentication.getPrincipal()).id()).isEqualTo(42);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CUSTOMER");
        assertThat(mdcInChain.get()).isEqualTo("42");
    }

    @Test
    void missingHeaderLeavesRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization(null);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE))
                .isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void nonBearerSchemeIsIgnored() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization("Basic dXNlcjpwYXNz");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE))
                .isNull();
    }

    @Test
    void expiredTokenIsRecordedAsExpired() throws Exception {
        String expired = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(-5), ISSUER))
                .generateToken(customer);
        MockHttpServletRequest request = requestWithAuthorization("Bearer " + expired);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE))
                .isEqualTo(JwtAuthenticationFilter.FAILURE_EXPIRED);
        assertThat(MDC.get("userId")).isNull();
        // The filter does not reject the request itself; the chain still continues.
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void malformedTokenIsRecordedAsInvalid() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization("Bearer abc.def.ghi");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE))
                .isEqualTo(JwtAuthenticationFilter.FAILURE_INVALID);
    }

    @Test
    void emptyBearerTokenIsRecordedAsInvalid() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization("Bearer   ");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE))
                .isEqualTo(JwtAuthenticationFilter.FAILURE_INVALID);
    }
}
