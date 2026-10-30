
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
 * Existing API behaviour, exercised through the security layer:
 * events are created by an ORGANISER and bookings by a CUSTOMER.
 * Organisers can access bookings only for events they own.
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
        ApiResult result = get(path, organiserToken);

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());

        return result.body();
    }

    private int getStatusCode(String path) {
        return get(path, organiserToken).status();
    }

    @Test
    void seatMapAndSummaryShouldWorkForEventWithoutBookings() {
        int eventId = createEvent();

        JsonNode seatResponse =
                get("/api/events/" + eventId + "/seats");

        assertThat(seatResponse.path("event_id").asInt())
                .isEqualTo(eventId);
        assertThat(seatResponse.path("total_seats").asInt())
                .isEqualTo(20);
        assertThat(seatResponse.path("booked_seats").isArray())
                .isTrue();
        assertThat(seatResponse.path("booked_seats").isEmpty())
                .isTrue();
        assertThat(seatResponse.path("available_seats").size())
                .isEqualTo(20);
        assertThat(seatResponse.path("available_seats").get(0).asInt())
                .isEqualTo(1);
        assertThat(seatResponse.path("available_seats").get(19).asInt())
                .isEqualTo(20);

        JsonNode summaryResponse =
                get("/api/events/" + eventId + "/summary");

        assertThat(summaryResponse.path("event_id").asInt())
                .isEqualTo(eventId);
        assertThat(summaryResponse.path("seats_sold").asInt())
                .isZero();
        assertThat(summaryResponse.path("seats_available").asInt())
                .isEqualTo(20);
        assertThat(summaryResponse.path("confirmed_bookings").asInt())
                .isZero();
        assertThat(summaryResponse.path("rejected_bookings").asInt())
                .isZero();
        assertThat(summaryResponse.path("total_revenue").decimalValue())
                .isEqualByComparingTo("0.00");
        assertThat(summaryResponse.path("occupancy_pct").decimalValue())
                .isEqualByComparingTo("0.0");
        assertThat(summaryResponse.path("demand_band").asText())
                .isEqualTo("LOW");
    }

    @Test
    void bookingFlowShouldUpdateSeatMapAndSummary() {
        int eventId = createEvent();

        createBooking(
                eventId,
                "first@example.com",
                List.of(3, 4),
                customerToken);

        JsonNode seatResponse =
                get("/api/events/" + eventId + "/seats");

        assertThat(seatResponse.path("booked_seats").toString())
                .isEqualTo("[3,4]");
        assertThat(seatResponse.path("available_seats").size())
                .isEqualTo(18);

        JsonNode summaryResponse =
                get("/api/events/" + eventId + "/summary");

        assertThat(summaryResponse.path("seats_sold").asInt())
                .isEqualTo(2);
        assertThat(summaryResponse.path("confirmed_bookings").asInt())
                .isEqualTo(1);
        assertThat(summaryResponse.path("seats_available").asInt())
                .isEqualTo(18);
    }

    @Test
    void summaryShouldReportHighDemandWhenMoreThan75PercentOfSeatsAreSold() {
        int eventId = createEvent();

        // Use a separate customer account for each booking.
        // Each customer stays within the six-seat limit per event.
        String firstCustomerToken = tokenFor(Role.CUSTOMER);
        String secondCustomerToken = tokenFor(Role.CUSTOMER);
        String thirdCustomerToken = tokenFor(Role.CUSTOMER);

        createBooking(
                eventId,
                "one@example.com",
                List.of(1, 2, 3, 4, 5, 6),
                firstCustomerToken);

        createBooking(
                eventId,
                "two@example.com",
                List.of(7, 8, 9, 10, 11, 12),
                secondCustomerToken);

        createBooking(
                eventId,
                "three@example.com",
                List.of(13, 14, 15, 16),
                thirdCustomerToken);

        JsonNode summaryResponse =
                get("/api/events/" + eventId + "/summary");

        assertThat(summaryResponse.path("seats_sold").asInt())
                .isEqualTo(16);
        assertThat(summaryResponse.path("seats_available").asInt())
                .isEqualTo(4);
        assertThat(summaryResponse.path("confirmed_bookings").asInt())
                .isEqualTo(3);
        assertThat(summaryResponse.path("occupancy_pct").decimalValue())
                .isEqualByComparingTo("80.0");
        assertThat(summaryResponse.path("demand_band").asText())
                .isEqualTo("HIGH");
    }

    @Test
    void bookingsEndpointShouldRejectInvalidStatus() {
        int eventId = createEvent();

        assertThat(getStatusCode(
                "/api/events/" + eventId + "/bookings?status=PENDING"))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void bookingListShouldReturnEmptyPageWhenNoBookingsExist() {
        int eventId = createEvent();

        JsonNode response =
                get("/api/events/" + eventId + "/bookings");

        // A paginated response stores records in the "content" array.
        assertThat(response.path("content").isArray()).isTrue();
        assertThat(response.path("content").isEmpty()).isTrue();

        // Pagination metadata should report that no bookings exist.
        assertThat(response.path("totalElements").asLong()).isZero();
        assertThat(response.path("totalPages").asInt()).isZero();
        assertThat(response.path("number").asInt()).isZero();
        assertThat(response.path("size").asInt()).isEqualTo(10);
    }

    @Test
    void bookingListShouldReturnConfirmedBookingsWhenStatusIsConfirmed() {
        int eventId = createEvent();

        createBooking(
                eventId,
                "confirmed@example.com",
                List.of(1, 2),
                customerToken);

        JsonNode response =
                get("/api/events/" + eventId + "/bookings?status=CONFIRMED");

        JsonNode bookings = response.path("content");

        assertThat(bookings.isArray()).isTrue();
        assertThat(bookings.size()).isEqualTo(1);
        assertThat(bookings.get(0).path("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(response.path("totalElements").asLong())
                .isEqualTo(1);
        assertThat(response.path("number").asInt()).isZero();
    }

    @Test
    void bookingListShouldReturnOnlyRequestedPage() {
        int eventId = createEvent();

        // Each booking uses different seats to avoid seat conflicts.
        createBooking(
                eventId,
                "page-one@example.com",
                List.of(1),
                tokenFor(Role.CUSTOMER));

        createBooking(
                eventId,
                "page-two@example.com",
                List.of(2),
                tokenFor(Role.CUSTOMER));

        createBooking(
                eventId,
                "page-three@example.com",
                List.of(3),
                tokenFor(Role.CUSTOMER));

        JsonNode firstPage =
                get("/api/events/" + eventId + "/bookings?page=0&size=2");

        assertThat(firstPage.path("content").size()).isEqualTo(2);
        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("number").asInt()).isZero();
        assertThat(firstPage.path("size").asInt()).isEqualTo(2);

        JsonNode secondPage =
                get("/api/events/" + eventId + "/bookings?page=1&size=2");

        assertThat(secondPage.path("content").size()).isEqualTo(1);
        assertThat(secondPage.path("totalElements").asLong()).isEqualTo(3);
        assertThat(secondPage.path("number").asInt()).isEqualTo(1);
        assertThat(secondPage.path("size").asInt()).isEqualTo(2);
    }

    @Test
    void bookingListShouldRejectNegativePageNumber() {
        int eventId = createEvent();

        assertThat(getStatusCode(
                "/api/events/" + eventId + "/bookings?page=-1&size=10"))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void bookingListShouldRejectPageSizeGreaterThanMaximum() {
        int eventId = createEvent();

        assertThat(getStatusCode(
                "/api/events/" + eventId + "/bookings?page=0&size=101"))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void bookingListShouldRejectZeroPageSize() {
        int eventId = createEvent();

        assertThat(getStatusCode(
                "/api/events/" + eventId + "/bookings?page=0&size=0"))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
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

        ApiResult result = post(
                "/api/events",
                organiserToken,
                request);

        assertThat(result.status())
                .isEqualTo(HttpStatus.CREATED.value());
        assertThat(result.body().path("id").asInt()).isPositive();

        return result.body().path("id").asInt();
    }

    private void createBooking(
            int eventId,
            String customerEmail,
            List<Integer> seatNumbers,
            String bookingCustomerToken) {

        Map<String, Object> request = Map.of(
                "event_id", eventId,
                "customer_name", "Integration Test Customer",
                "customer_email", customerEmail,
                "seat_numbers", seatNumbers);

        ApiResult result = post(
                "/api/bookings",
                bookingCustomerToken,
                request);

        assertThat(result.status())
                .as("Booking API response: " + result.body())
                .isEqualTo(HttpStatus.CREATED.value());

        assertThat(result.body().path("id").asInt()).isPositive();

        assertThat(result.body().path("status").asText())
                .as("Booking API response: " + result.body())
                .isEqualTo("CONFIRMED");
    }
}
