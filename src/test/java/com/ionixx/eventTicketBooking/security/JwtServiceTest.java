package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.ionixx.eventticketbooking.entity.Role;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijkl";
    private static final String OTHER_SECRET = "another-secret-0123456789-abcdefghijkl";
    private static final String ISSUER = "event-ticket-booking";

    private static JwtService serviceWith(
            String secret, Duration expiration, String issuer) {
        return new JwtService(new JwtProperties(secret, expiration, issuer));
    }

    private static UserPrincipal organiser() {
        return new UserPrincipal(7, "org@example.com", "hash", Role.ORGANISER);
    }

    @Test
    void generatedTokenCanBeParsedBackIntoTheSameUser() {
        JwtService service = serviceWith(SECRET, Duration.ofMinutes(5), ISSUER);

        String token = service.generateToken(organiser());
        UserPrincipal parsed = service.parseToken(token);

        assertThat(parsed.id()).isEqualTo(7);
        assertThat(parsed.email()).isEqualTo("org@example.com");
        assertThat(parsed.role()).isEqualTo(Role.ORGANISER);
        // The password hash is never put in the token.
        assertThat(parsed.getPassword()).isNull();
    }

    @Test
    void expiredTokenIsRejectedWithExpiredJwtException() {
        JwtService service = serviceWith(SECRET, Duration.ofMinutes(-5), ISSUER);
        String token = service.generateToken(organiser());

        assertThatThrownBy(() -> service.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = serviceWith(OTHER_SECRET, Duration.ofMinutes(5), ISSUER)
                .generateToken(organiser());

        JwtService service = serviceWith(SECRET, Duration.ofMinutes(5), ISSUER);

        assertThatThrownBy(() -> service.parseToken(forged))
                .isInstanceOf(JwtException.class)
                .isNotInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = serviceWith(SECRET, Duration.ofMinutes(5), "someone-else")
                .generateToken(organiser());

        JwtService service = serviceWith(SECRET, Duration.ofMinutes(5), ISSUER);

        assertThatThrownBy(() -> service.parseToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        JwtService service = serviceWith(SECRET, Duration.ofMinutes(5), ISSUER);

        assertThatThrownBy(() -> service.parseToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        JwtService service = serviceWith(SECRET, Duration.ofMinutes(5), ISSUER);

        // {"alg":"none"} . {"sub":"7"} . (no signature)
        String unsigned = "eyJhbGciOiJub25lIn0.eyJzdWIiOiI3In0.";

        assertThatThrownBy(() -> service.parseToken(unsigned))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan32BytesIsRejectedAtStartup() {
        assertThatThrownBy(() ->
                serviceWith("too-short", Duration.ofMinutes(5), ISSUER))
                .isInstanceOf(IllegalStateException.class);
    }
}
