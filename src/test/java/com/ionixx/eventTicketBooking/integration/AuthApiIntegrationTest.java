package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.security.JwtProperties;
import com.ionixx.eventticketbooking.security.JwtService;
import com.ionixx.eventticketbooking.security.UserPrincipal;

class AuthApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtProperties jwtProperties;

    // ---- POST /auth/login -------------------------------------------------

    @Test
    void loginWithCorrectCredentialsReturnsSignedToken() {
        String email = createUser(Role.CUSTOMER);

        ApiResult result = post("/auth/login", null,
                Map.of("email", email, "password", PASSWORD));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().path("accessToken").asText()).isNotBlank();
        // header.payload.signature
        assertThat(result.body().path("accessToken").asText().split("\\.")).hasSize(3);
        assertThat(result.body().path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(result.body().path("expiresIn").asLong()).isPositive();
        assertThat(result.body().path("role").asText()).isEqualTo("CUSTOMER");
        assertThat(result.body().path("email").asText()).isEqualTo(email);
        // The response must never contain the password or its hash.
        assertThat(result.body().toString()).doesNotContain("password");
    }

    @Test
    void loginEmailIsCaseInsensitive() {
        String email = createUser(Role.ORGANISER);

        ApiResult result = post("/auth/login", null,
                Map.of("email", email.toUpperCase(), "password", PASSWORD));

        assertThat(result.status()).isEqualTo(200);
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        String email = createUser(Role.CUSTOMER);

        ApiResult result = post("/auth/login", null,
                Map.of("email", email, "password", "wrong-password"));

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body().path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void loginWithUnknownEmailReturnsSame401AsWrongPassword() {
        ApiResult result = post("/auth/login", null,
                Map.of("email", "nobody@test.com", "password", PASSWORD));

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body().path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(result.body().path("message").asText())
                .isEqualTo("Invalid email or password");
    }

    @Test
    void loginWithMissingFieldsReturns400() {
        ApiResult result = post("/auth/login", null, Map.of("email", "a@test.com"));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void loginWithInvalidEmailFormatReturns400() {
        ApiResult result = post("/auth/login", null,
                Map.of("email", "not-an-email", "password", PASSWORD));

        assertThat(result.status()).isEqualTo(400);
    }

    @Test
    void passwordsAreStoredAsBCryptHashes() {
        String email = createUser(Role.CUSTOMER);

        User stored = userRepository.findByEmail(email).orElseThrow();

        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    // ---- JWT validation on protected endpoints ----------------------------

    @Test
    void protectedEndpointWithoutTokenReturns401() {
        ApiResult result = get("/api/events/1", null);

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body().path("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(result.headers().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer");
        assertThat(result.body().path("path").asText()).isEqualTo("/api/events/1");
    }

    @Test
    void protectedEndpointWithValidTokenIsAllowedThrough() {
        String token = tokenFor(Role.CUSTOMER);

        // Event 999999 does not exist: 404 proves we got past security to the controller.
        ApiResult result = get("/api/events/999999", token);

        assertThat(result.status()).isEqualTo(404);
        assertThat(result.body().path("code").asText()).isEqualTo("EVENT_NOT_FOUND");
    }

    @Test
    void malformedTokenReturns401InvalidToken() {
        ApiResult result = get("/api/events/1", "abc.def.ghi");

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body().path("code").asText()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void expiredTokenReturns401TokenExpired() {
        String email = createUser(Role.CUSTOMER);
        String expired = tokenFromService(email,
                jwtProperties.secret(), Duration.ofMinutes(-5));

        ApiResult result = get("/api/events/1", expired);

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body().path("code").asText()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void tokenSignedWithDifferentSecretReturns401InvalidToken() {
        String email = createUser(Role.ORGANISER);
        String forged = tokenFromService(email,
                "a-completely-different-secret-0123456789", Duration.ofMinutes(5));

        ApiResult result = get("/api/events/1", forged);

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body().path("code").asText()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void badTokenOnPublicLoginEndpointDoesNotBreakLogin() {
        // A stale token in the browser must not stop the user from logging in again.
        String email = createUser(Role.CUSTOMER);

        ApiResult result = call(
                org.springframework.http.HttpMethod.POST, "/auth/login", "stale.token.value",
                Map.of("email", email, "password", PASSWORD));

        assertThat(result.status()).isEqualTo(200);
    }

    @Test
    void healthEndpointIsPublic() {
        assertThat(get("/actuator/health", null).status()).isEqualTo(200);
    }

    private String tokenFromService(String email, String secret, Duration expiration) {
        User user = userRepository.findByEmail(email).orElseThrow();
        JwtService service = new JwtService(
                new JwtProperties(secret, expiration, jwtProperties.issuer()));
        return service.generateToken(UserPrincipal.from(user));
    }
}
