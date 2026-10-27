package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.ionixx.eventticketbooking.entity.Role;

@ExtendWith(OutputCaptureExtension.class)
class RoleAccessIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> eventRequest() {
        return Map.of(
                "name", "Role Test Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00);
    }

    private Map<String, Object> bookingRequest(int eventId) {
        return Map.of(
                "event_id", eventId,
                "customer_name", "Role Test Customer",
                "customer_email", "role-test@example.com",
                "seat_numbers", List.of(1, 2));
    }

    private int createEventAsOrganiser() {
        ApiResult result = post("/api/events", tokenFor(Role.ORGANISER), eventRequest());
        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    // ---- POST /api/events : ORGANISER only --------------------------------

    @Test
    void organiserCanCreateEvent() {
        ApiResult result = post("/api/events", tokenFor(Role.ORGANISER), eventRequest());

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("eventLogId").asText()).startsWith("ETB");
    }

    @Test
    void customerCannotCreateEventAndGets403() {
        ApiResult result = post("/api/events", tokenFor(Role.CUSTOMER), eventRequest());

        assertThat(result.status()).isEqualTo(403);
        assertThat(result.body().path("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void anonymousCannotCreateEventAndGets401() {
        ApiResult result = post("/api/events", null, eventRequest());

        assertThat(result.status()).isEqualTo(401);
    }

    // ---- POST /api/bookings : CUSTOMER only -------------------------------

    @Test
    void customerCanCreateBooking() {
        int eventId = createEventAsOrganiser();

        ApiResult result = post("/api/bookings",
                tokenFor(Role.CUSTOMER), bookingRequest(eventId));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void organiserCannotCreateBookingAndGets403() {
        int eventId = createEventAsOrganiser();

        ApiResult result = post("/api/bookings",
                tokenFor(Role.ORGANISER), bookingRequest(eventId));

        assertThat(result.status()).isEqualTo(403);
        assertThat(result.body().path("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void anonymousCannotCreateBookingAndGets401() {
        ApiResult result = post("/api/bookings", null, bookingRequest(1));

        assertThat(result.status()).isEqualTo(401);
    }

    // ---- Read endpoints : any logged-in user ------------------------------

    @Test
    void anyAuthenticatedRoleCanReadEventEndpoints() {
        int eventId = createEventAsOrganiser();

        for (Role role : Role.values()) {
            String token = tokenFor(role);

            assertThat(get("/api/events/" + eventId, token).status()).isEqualTo(200);
            assertThat(get("/api/events/" + eventId + "/seats", token).status())
                    .isEqualTo(200);
            assertThat(get("/api/events/" + eventId + "/summary", token).status())
                    .isEqualTo(200);
            assertThat(get("/api/events/" + eventId + "/bookings", token).status())
                    .isEqualTo(200);
        }
    }

    @Test
    void anonymousCannotReadEventEndpoints() {
        assertThat(get("/api/events/1", null).status()).isEqualTo(401);
        assertThat(get("/api/events/1/seats", null).status()).isEqualTo(401);
        assertThat(get("/api/events/1/summary", null).status()).isEqualTo(401);
        assertThat(get("/api/events/1/bookings", null).status()).isEqualTo(401);
        assertThat(get("/api/bookings/1", null).status()).isEqualTo(401);
    }

    // ---- Existing validation still works behind security ------------------

    @Test
    void validationErrorsStillReturn400ForAuthorisedUsers() {
        ApiResult result = post("/api/events", tokenFor(Role.ORGANISER), Map.of());

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    // ---- MDC / logging ----------------------------------------------------

    @Test
    void authenticatedRequestLogsContainTheUserId(CapturedOutput output) {
        String email = createUser(Role.ORGANISER);
        String token = loginAndGetToken(email, PASSWORD);
        int userId = userRepository.findByEmail(email).orElseThrow().getId();

        ApiResult result = post("/api/events", token, eventRequest());
        assertThat(result.status()).isEqualTo(201);

        // The "HTTP request completed" line is logged just after the response is sent.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(output.getAll())
                        .contains("\"userId\":\"" + userId + "\"")
                        .contains("Event created successfully"));
    }
}