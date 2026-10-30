package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.repository.BookingRepository;

/**
 * The identity stored on a booking (customer id AND email) must come from the
 * authenticated account. Sending another customer's email in the body must
 * not put that customer's name on the booking.
 */
class BookingIdentityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    private int createEvent() {
        ApiResult result = post("/api/events", tokenFor(Role.ORGANISER), Map.of(
                "name", "Identity Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    @Test
    void emailOfAnotherCustomerInTheBodyIsIgnored() {
        int eventId = createEvent();

        String myEmail = createUser(Role.CUSTOMER);
        String myToken = loginAndGetToken(myEmail, PASSWORD);
        String victimEmail = createUser(Role.CUSTOMER);

        int myId = userRepository.findByEmail(myEmail).orElseThrow().getId();

        Map<String, Object> body = new HashMap<>();
        body.put("event_id", eventId);
        body.put("customer_name", "Identity Customer");
        body.put("customer_email", victimEmail);
        body.put("seat_numbers", List.of(1));

        ApiResult result = post("/api/bookings", myToken, body);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("customer_email").asText()).isEqualTo(myEmail);

        // Check the stored row, not only the HTTP response.
        Booking stored = bookingRepository
                .findById(result.body().path("id").asInt())
                .orElseThrow();

        assertThat(stored.getCustomerId()).isEqualTo(myId);
        assertThat(stored.getCustomerEmail()).isEqualTo(myEmail);
        assertThat(stored.getCustomerEmail()).isNotEqualTo(victimEmail);
    }

    @Test
    void customerEmailMayBeOmitted() {
        int eventId = createEvent();
        String email = createUser(Role.CUSTOMER);
        String token = loginAndGetToken(email, PASSWORD);

        ApiResult result = post("/api/bookings", token, Map.of(
                "event_id", eventId,
                "customer_name", "Identity Customer",
                "seat_numbers", List.of(2)));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("customer_email").asText()).isEqualTo(email);
    }

    @Test
    void malformedCustomerEmailIsStillRejected() {
        int eventId = createEvent();
        String token = tokenFor(Role.CUSTOMER);

        ApiResult result = post("/api/bookings", token, Map.of(
                "event_id", eventId,
                "customer_name", "Identity Customer",
                "customer_email", "not-an-email",
                "seat_numbers", List.of(3)));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }
}