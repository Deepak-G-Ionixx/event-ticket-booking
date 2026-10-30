package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.EventRepository;

/**
 * Verifies that one organiser cannot access another organiser's event
 * and one customer cannot access another customer's booking.
 *
 * Authenticated users who lack permission receive HTTP 403 Forbidden.
 * Unauthenticated requests should receive HTTP 401 Unauthorized.
 */
class OwnershipIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private int createEvent(String organiserToken) {
        ApiResult result = post("/api/events", organiserToken, Map.of(
                "name", "Ownership Test Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    private int createBooking(int eventId, String customerToken, List<Integer> seats) {
        ApiResult result = post("/api/bookings", customerToken, Map.of(
                "event_id", eventId,
                "customer_name", "Ownership Customer",
                "customer_email", "owner-" + UUID.randomUUID() + "@example.com",
                "seat_numbers", seats));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    private static List<String> eventPaths(int eventId) {
        return List.of(
                "/api/events/" + eventId,
                "/api/events/" + eventId + "/seats",
                "/api/events/" + eventId + "/summary",
                "/api/events/" + eventId + "/bookings");
    }

    // ---- organiser vs organiser ------------------------------------------

    @Test
    void anotherOrganiserGets403OnEveryEndpointOfSomeoneElsesEvent() {
        String ownerToken = tokenFor(Role.ORGANISER);
        String otherToken = tokenFor(Role.ORGANISER);
        int eventId = createEvent(ownerToken);

        for (String path : eventPaths(eventId)) {
            assertThat(get(path, ownerToken).status())
                    .as("owner " + path)
                    .isEqualTo(200);

            ApiResult denied = get(path, otherToken);

            assertThat(denied.status())
                    .as("other organiser " + path)
                    .isEqualTo(403);

            assertThat(denied.body().path("code").asText())
                    .isEqualTo("RESOURCE_ACCESS_DENIED");
        }
    }

    @Test
    void eventsAndBookingsRememberWhoCreatedThem() {
        String organiserEmail = createUser(Role.ORGANISER);
        String customerEmail = createUser(Role.CUSTOMER);

        String organiserToken = loginAndGetToken(organiserEmail, PASSWORD);
        String customerToken = loginAndGetToken(customerEmail, PASSWORD);

        int eventId = createEvent(organiserToken);
        int bookingId = createBooking(eventId, customerToken, List.of(1, 2));

        Integer organiserId =
                userRepository.findByEmail(organiserEmail).orElseThrow().getId();
        Integer customerId =
                userRepository.findByEmail(customerEmail).orElseThrow().getId();

        assertThat(eventRepository.findById(eventId).orElseThrow().getOrganiserId())
                .isEqualTo(organiserId);

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getCustomerId())
                .isEqualTo(customerId);
    }

    // ---- customer vs customer --------------------------------------------

    @Test
    void customerCannotReadAnotherCustomersBooking() {
        String organiserToken = tokenFor(Role.ORGANISER);
        String otherOrganiserToken = tokenFor(Role.ORGANISER);
        String ownerToken = tokenFor(Role.CUSTOMER);
        String strangerToken = tokenFor(Role.CUSTOMER);

        int eventId = createEvent(organiserToken);
        int bookingId = createBooking(eventId, ownerToken, List.of(1, 2));
        String path = "/api/bookings/" + bookingId;

        assertThat(get(path, ownerToken).status()).isEqualTo(200);

        // The event organiser can view bookings for their own event.
        assertThat(get(path, organiserToken).status()).isEqualTo(200);

        // Another authenticated customer cannot view this booking.
        ApiResult strangerResult = get(path, strangerToken);

        assertThat(strangerResult.status()).isEqualTo(403);
        assertThat(strangerResult.body().path("code").asText())
                .isEqualTo("RESOURCE_ACCESS_DENIED");

        // An unrelated organiser cannot view this booking either.
        assertThat(get(path, otherOrganiserToken).status()).isEqualTo(403);
    }

    // ---- customers browsing ----------------------------------------------

    @Test
    void customersCanBrowseAnyEventAndItsSeats() {
        int eventId = createEvent(tokenFor(Role.ORGANISER));
        String customerToken = tokenFor(Role.CUSTOMER);

        assertThat(get("/api/events/" + eventId, customerToken).status())
                .isEqualTo(200);

        assertThat(get("/api/events/" + eventId + "/seats", customerToken).status())
                .isEqualTo(200);
    }

    // ---- events must always have an owner ---------------------------------

    @Test
    void databaseRejectsAnEventWithoutAnOwner() {
        // Migration 010 makes event.organiser_id NOT NULL on a database that
        // has no ownerless rows (such as this fresh Testcontainers database).
        Event ownerless = new Event(
                "Ownerless Event " + UUID.randomUUID(),
                "Chennai Hall",
                EventCategory.TECH,
                LocalDate.now().plusDays(60),
                20,
                new BigDecimal("1000.00"));

        assertThatThrownBy(() -> eventRepository.saveAndFlush(ownerless))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
