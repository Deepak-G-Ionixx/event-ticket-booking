package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;

/**
 * POST /auth/register with a role: ORGANISER or CUSTOMER.
 */
class RoleRegistrationIntegrationTest extends AbstractIntegrationTest {

    private static String newEmail() {
        return "reg-" + UUID.randomUUID() + "@test.com";
    }

    private Map<String, Object> registration(String email, String role) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New User");
        body.put("email", email);
        body.put("password", PASSWORD);
        if (role != null) {
            body.put("role", role);
        }
        return body;
    }

    @Test
    void organiserRoleCreatesAnOrganiser() {
        String email = newEmail();

        ApiResult result = post("/auth/register", null, registration(email, "ORGANISER"));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("role").asText()).isEqualTo("ORGANISER");
        assertThat(result.body().toString()).doesNotContain("password");

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.ORGANISER);
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void customerRoleCreatesACustomer() {
        String email = newEmail();

        ApiResult result = post("/auth/register", null, registration(email, "CUSTOMER"));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("role").asText()).isEqualTo("CUSTOMER");
        assertThat(userRepository.findByEmail(email).orElseThrow().getRole())
                .isEqualTo(Role.CUSTOMER);
    }

    @Test
    void leavingTheRoleOutCreatesACustomer() {
        ApiResult result = post("/auth/register", null, registration(newEmail(), null));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("role").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void unknownRolesAreRejectedWith400() {
        for (String role : List.of("ADMIN", "SUPERUSER")) {
            String email = newEmail();

            ApiResult result = post("/auth/register", null, registration(email, role));

            assertThat(result.status()).as(role).isEqualTo(400);
            assertThat(result.body().path("code").asText()).isEqualTo("INVALID_REQUEST_BODY");
            assertThat(userRepository.existsByEmail(email)).as(role).isFalse();
        }
    }

    @Test
    void duplicateEmailReturns409() {
        String email = newEmail();
        assertThat(post("/auth/register", null, registration(email, "ORGANISER")).status())
                .isEqualTo(201);

        ApiResult second = post("/auth/register", null, registration(email, "CUSTOMER"));

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().path("code").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void registeredOrganiserLogsInWithOrganiserRoleAndCanCreateEvents() {
        String email = newEmail();
        assertThat(post("/auth/register", null, registration(email, "ORGANISER")).status())
                .isEqualTo(201);

        ApiResult login = post("/auth/login", null,
                Map.of("email", email, "password", PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().path("role").asText()).isEqualTo("ORGANISER");

        String token = login.body().path("accessToken").asText();
        ApiResult event = post("/api/events", token, Map.of(
                "name", "Registered Organiser Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00));
        assertThat(event.status()).isEqualTo(201);
    }

    @Test
    void registeredCustomerCannotCreateEvents() {
        String email = newEmail();
        assertThat(post("/auth/register", null, registration(email, "CUSTOMER")).status())
                .isEqualTo(201);

        String token = loginAndGetToken(email, PASSWORD);
        ApiResult event = post("/api/events", token, Map.of(
                "name", "Customer Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00));

        assertThat(event.status()).isEqualTo(403);
    }
}