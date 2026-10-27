package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ionixx.eventticketbooking.entity.Role;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtBranchCoverageTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijkl";
    private static final String ISSUER = "event-ticket-booking";

    private final JwtService service =
            new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(5), ISSUER));

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    /** A token signed with the real key and issuer, so only its claims can be wrong. */
    private JwtBuilder validlySignedBuilder() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer(ISSUER)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, Jwts.SIG.HS256);
    }

    // ------------------------------------------------------------------
    // JwtService
    // ------------------------------------------------------------------

    @Test
    void nullSecretIsRejected() {
        assertThatThrownBy(() ->
                new JwtService(new JwtProperties(null, Duration.ofMinutes(5), ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void missingExpirationIsRejected() {
        assertThatThrownBy(() ->
                new JwtService(new JwtProperties(SECRET, null, ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiration must be configured");
    }

    @Test
    void expiresInSecondsMatchesConfiguredDuration() {
        assertThat(service.getExpiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void tokenWithUnknownRoleIsTreatedAsMalformed() {
        String token = validlySignedBuilder()
                .subject("7")
                .claim(JwtService.CLAIM_EMAIL, "a@example.com")
                .claim(JwtService.CLAIM_ROLE, "SUPERUSER")
                .compact();

        assertThatThrownBy(() -> service.parseToken(token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessage("Token claims are invalid");
    }

    @Test
    void tokenWithoutRoleClaimIsTreatedAsMalformed() {
        String token = validlySignedBuilder()
                .subject("7")
                .claim(JwtService.CLAIM_EMAIL, "a@example.com")
                .compact();

        assertThatThrownBy(() -> service.parseToken(token))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void tokenWithNonNumericSubjectIsTreatedAsMalformed() {
        String token = validlySignedBuilder()
                .subject("not-a-number")
                .claim(JwtService.CLAIM_EMAIL, "a@example.com")
                .claim(JwtService.CLAIM_ROLE, "CUSTOMER")
                .compact();

        assertThatThrownBy(() -> service.parseToken(token))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void tokenWithoutSubjectIsTreatedAsMalformed() {
        String token = validlySignedBuilder()
                .claim(JwtService.CLAIM_EMAIL, "a@example.com")
                .claim(JwtService.CLAIM_ROLE, "CUSTOMER")
                .compact();

        assertThatThrownBy(() -> service.parseToken(token))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void tokenWithValidClaimsIsParsed() {
        String token = validlySignedBuilder()
                .subject("12")
                .claim(JwtService.CLAIM_EMAIL, "ok@example.com")
                .claim(JwtService.CLAIM_ROLE, "ORGANISER")
                .compact();

        UserPrincipal principal = service.parseToken(token);

        assertThat(principal.id()).isEqualTo(12);
        assertThat(principal.email()).isEqualTo("ok@example.com");
        assertThat(principal.role()).isEqualTo(Role.ORGANISER);
    }

    // ------------------------------------------------------------------
    // JwtAuthenticationFilter
    // ------------------------------------------------------------------

    @Test
    void bearerSchemeIsMatchedCaseInsensitively() throws Exception {
        String token = service.generateToken(
                new UserPrincipal(3, "c@example.com", "hash", Role.CUSTOMER));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/1");
        request.addHeader("Authorization", "bEaReR " + token);

        new JwtAuthenticationFilter(service)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // The filter chain ran; the context is the one the filter built for this request.
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID)).isEqualTo("3");
    }

    @Test
    void headerShorterThanBearerPrefixIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/1");
        request.addHeader("Authorization", "Bear");

        new JwtAuthenticationFilter(service)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unexpectedRuntimeErrorWhileParsingIsRecordedAsInvalid() throws Exception {
        JwtService failingService = mock(JwtService.class);
        when(failingService.parseToken("boom-token"))
                .thenThrow(new IllegalStateException("unexpected"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/1");
        request.addHeader("Authorization", "Bearer boom-token");

        new JwtAuthenticationFilter(failingService)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE))
                .isEqualTo(JwtAuthenticationFilter.FAILURE_INVALID);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}