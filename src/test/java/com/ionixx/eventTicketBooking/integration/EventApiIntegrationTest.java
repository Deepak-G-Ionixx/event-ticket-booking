package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ionixx.eventticketbooking.entity.Role;

import tools.jackson.databind.JsonNode;

/**
 * Existing API behaviour, now exercised through the security layer:
 * events are created by an ORGANISER, bookings by a CUSTOMER, reads by either.
 */
class EventApiIntegrationTest extends AbstractIntegrationTest {

    private String organiserToken;
    private String customerToken;

    @BeforeEach
    void logIn() {
        organiserToken = tokenFor(Role.ORGANISER);
        customerToken = tokenFor(Role.CUSTOMER);
    }

    private JsonNode get(String path) {
        ApiResult result = get(path, customerToken);
        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        return result.body();
    }

    private int getStatusCode(String path) {
        return get(path, customerToken).status();
    }

    @Test
    void seatMapAndSummaryShouldWorkForEventWithoutBookings() {
        int eventId = createEvent();

        JsonNode seatResponse = get("/api/events/" + eventId + "/seats");

        assertThat(seatResponse.path("event_id").asInt()).isEqualTo(eventId);
        assertThat(seatResponse.path("total_seats").asInt()).isEqualTo(20);
        assertThat(seatResponse.path("booked_seats").isArray()).isTrue();
        assertThat(seatResponse.path("booked_seats").isEmpty()).isTrue();
        assertThat(seatResponse.path("available_seats").size()).isEqualTo(20);
        assertThat(seatResponse.path("available_seats").get(0).asInt()).isEqualTo(1);
        assertThat(seatResponse.path("available_seats").get(19).asInt()).isEqualTo(20);

        JsonNode summaryResponse = get("/api/events/" + eventId + "/summary");

        assertThat(summaryResponse.path("event_id").asInt()).isEqualTo(eventId);
        assertThat(summaryResponse.path("seats_sold").asInt()).isZero();
        assertThat(summaryResponse.path("seats_available").asInt()).isEqualTo(20);
        assertThat(summaryResponse.path("confirmed_bookings").asInt()).isZero();
        assertThat(summaryResponse.path("rejected_bookings").asInt()).isZero();
        assertThat(summaryResponse.path("total_revenue").decimalValue())
                .isEqualByComparingTo("0.00");
        assertThat(summaryResponse.path("occupancy_pct").decimalValue())
                .isEqualByComparingTo("0.0");
        assertThat(summaryResponse.path("demand_band").asText()).isEqualTo("LOW");
    }

    @Test
    void bookingFlowShouldUpdateSeatMapAndSummary() {
        int eventId = createEvent();

        createBooking(eventId, "first@example.com", List.of(3, 4));

        JsonNode seatResponse = get("/api/events/" + eventId + "/seats");

        assertThat(seatResponse.path("booked_seats").toString()).isEqualTo("[3,4]");
        assertThat(seatResponse.path("available_seats").size()).isEqualTo(18);

        JsonNode summaryResponse = get("/api/events/" + eventId + "/summary");

        assertThat(summaryResponse.path("seats_sold").asInt()).isEqualTo(2);
        assertThat(summaryResponse.path("confirmed_bookings").asInt()).isEqualTo(1);
        assertThat(summaryResponse.path("seats_available").asInt()).isEqualTo(18);
    }

    @Test
    void summaryShouldReportHighDemandWhenMoreThan75PercentOfSeatsAreSold() {
        int eventId = createEvent();

        createBooking(eventId, "one@example.com", List.of(1, 2, 3, 4, 5, 6));
        createBooking(eventId, "two@example.com", List.of(7, 8, 9, 10, 11, 12));
        createBooking(eventId, "three@example.com", List.of(13, 14, 15, 16));

        JsonNode summaryResponse = get("/api/events/" + eventId + "/summary");

        assertThat(summaryResponse.path("seats_sold").asInt()).isEqualTo(16);
        assertThat(summaryResponse.path("seats_available").asInt()).isEqualTo(4);
        assertThat(summaryResponse.path("confirmed_bookings").asInt()).isEqualTo(3);
        assertThat(summaryResponse.path("occupancy_pct").decimalValue())
                .isEqualByComparingTo("80.0");
        assertThat(summaryResponse.path("demand_band").asText()).isEqualTo("HIGH");
    }

    @Test
    void bookingsEndpointShouldRejectInvalidStatus() {
        int eventId = createEvent();

        assertThat(getStatusCode(
                "/api/events/" + eventId + "/bookings?status=PENDING"))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void bookingListShouldReturnEmptyArrayWhenNoBookingsExist() {
        int eventId = createEvent();

        JsonNode response = get("/api/events/" + eventId + "/bookings");

        assertThat(response.isArray()).isTrue();
        assertThat(response.isEmpty()).isTrue();
    }

    @Test
    void bookingListShouldReturnConfirmedBookingsWhenStatusIsConfirmed() {
        int eventId = createEvent();

        createBooking(eventId, "confirmed@example.com", List.of(1, 2));

        JsonNode response =
                get("/api/events/" + eventId + "/bookings?status=CONFIRMED");

        assertThat(response.isArray()).isTrue();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.get(0).path("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void endpointsShouldReturnNotFoundForMissingEvent() {
        assertThat(getStatusCode("/api/events/999999/seats"))
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(getStatusCode("/api/events/999999/summary"))
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(getStatusCode("/api/events/999999/bookings"))
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void seatMapShouldRejectNonNumericEventId() {
        assertThat(getStatusCode("/api/events/abc/seats"))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void summaryShouldRejectNonNumericEventId() {
        assertThat(getStatusCode("/api/events/abc/summary"))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    private int createEvent() {
        Map<String, Object> request = Map.of(
                "name", "Integration Test Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00);

        ApiResult result = post("/api/events", organiserToken, request);

        assertThat(result.status()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(result.body().path("id").asInt()).isPositive();

        return result.body().path("id").asInt();
    }

    private void createBooking(
            int eventId,
            String customerEmail,
            List<Integer> seatNumbers) {

        Map<String, Object> request = Map.of(
                "event_id", eventId,
                "customer_name", "Integration Test Customer",
                "customer_email", customerEmail,
                "seat_numbers", seatNumbers);

        ApiResult result = post("/api/bookings", customerToken, request);

        assertThat(result.status()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(result.body().path("id").asInt()).isPositive();
        assertThat(result.body().path("status").asText()).isEqualTo("CONFIRMED");
    }
}
