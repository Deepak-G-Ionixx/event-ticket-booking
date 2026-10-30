package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;

import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.EventRepository;

import tools.jackson.databind.JsonNode;

/**
 * Two organisers and two customers, each with their own JWT.
 * Every "denied" test also reads the database afterwards, because an HTTP
 * 403 alone does not prove that nothing was changed.
 */
class SecurityIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private record Account(String email, String token, int id) {
    }

    private Account account(Role role) {
        String email = createUser(role);
        String token = loginAndGetToken(email, PASSWORD);
        int id = userRepository.findByEmail(email).orElseThrow().getId();
        return new Account(email, token, id);
    }

    private static Map<String, Object> eventBody(String name, int totalSeats) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("venue", "Chennai Test Hall");
        body.put("category", "TECH");
        body.put("eventDate", LocalDate.now().plusDays(60).toString());
        body.put("totalSeats", totalSeats);
        body.put("basePrice", 1500.00);
        return body;
    }

    private int createEvent(Account organiser) {
        ApiResult result = post("/api/events", organiser.token(),
                eventBody("Isolation Event " + UUID.randomUUID(), 20));
        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    private ApiResult book(Account customer, int eventId, String emailInBody, List<Integer> seats) {
        return post("/api/bookings", customer.token(), Map.of(
                "event_id", eventId,
                "customer_name", "Isolation Customer",
                "customer_email", emailInBody,
                "seat_numbers", seats));
    }

    private static Set<Integer> ids(JsonNode page) {
        Set<Integer> ids = new HashSet<>();
        page.path("content").forEach(node -> ids.add(node.path("id").asInt()));
        return ids;
    }

    // ---- authentication ---------------------------------------------------

    @Test
    void requestsWithoutAValidTokenAreRejectedWith401() {
        Map<String, Object> bookingBody = Map.of(
                "event_id", 1,
                "customer_name", "No Token",
                "customer_email", "none@example.com",
                "seat_numbers", List.of(1));

        assertThat(post("/api/events", null, eventBody("No Token Event", 20)).status())
                .isEqualTo(401);
        assertThat(post("/api/bookings", null, bookingBody).status())
                .isEqualTo(401);

        assertThat(post("/api/events", "not-a-jwt", eventBody("Bad Token Event", 20)).status())
                .isEqualTo(401);
        assertThat(get("/api/bookings/mine", "not-a-jwt").status())
                .isEqualTo(401);
    }

    // ---- roles ------------------------------------------------------------

    @Test
    void customerCannotUseOrganiserEndpoints() {
        Account customer = account(Role.CUSTOMER);

        assertThat(post("/api/events", customer.token(), eventBody("By Customer", 20)).status())
                .isEqualTo(403);
        assertThat(call(HttpMethod.PUT, "/api/events/1", customer.token(),
                eventBody("By Customer", 20)).status()).isEqualTo(403);
        assertThat(call(HttpMethod.DELETE, "/api/events/1", customer.token(), null).status())
                .isEqualTo(403);
        assertThat(get("/api/events/mine", customer.token()).status())
                .isEqualTo(403);
    }

    @Test
    void organiserCannotUseCustomerEndpoints() {
        Account organiser = account(Role.ORGANISER);
        int eventId = createEvent(organiser);

        assertThat(book(organiser, eventId, organiser.email(), List.of(1)).status())
                .isEqualTo(403);
        assertThat(get("/api/bookings/mine", organiser.token()).status())
                .isEqualTo(403);
    }

    // ---- organiser isolation ----------------------------------------------

    @Test
    void eventIsOwnedByTheLoggedInOrganiserEvenWhenBodyNamesSomeoneElse() {
        Account a = account(Role.ORGANISER);
        Account b = account(Role.ORGANISER);

        Map<String, Object> body = eventBody("Owner Spoof " + UUID.randomUUID(), 20);
        body.put("organiserId", b.id());
        body.put("organiser_id", b.id());

        ApiResult result = post("/api/events", a.token(), body);

        // Either the extra field is ignored (201) or rejected (400); it must never be obeyed.
        assertThat(result.status()).isIn(201, 400);

        if (result.status() == 201) {
            int eventId = result.body().path("id").asInt();
            assertThat(eventRepository.findById(eventId).orElseThrow().getOrganiserId())
                    .isEqualTo(a.id());
        }

        assertThat(eventRepository.findAllByOrganiserId(b.id())).isEmpty();
    }

    @Test
    void anotherOrganiserCannotUpdateOrDeleteAndTheDatabaseStaysUnchanged() {
        Account a = account(Role.ORGANISER);
        Account b = account(Role.ORGANISER);
        int eventId = createEvent(a);
        String path = "/api/events/" + eventId;

        Event before = eventRepository.findById(eventId).orElseThrow();
        String originalName = before.getName();
        Integer originalSeats = before.getTotalSeats();

        ApiResult put = call(HttpMethod.PUT, path, b.token(),
                eventBody("Hijacked " + UUID.randomUUID(), 50));
        assertThat(put.status()).isEqualTo(403);
        assertThat(put.body().path("code").asText()).isEqualTo("RESOURCE_ACCESS_DENIED");

        ApiResult delete = call(HttpMethod.DELETE, path, b.token(), null);
        assertThat(delete.status()).isEqualTo(403);

        // Read the row again: the HTTP status alone proves nothing.
        Event after = eventRepository.findById(eventId).orElseThrow();
        assertThat(after.getName()).isEqualTo(originalName);
        assertThat(after.getTotalSeats()).isEqualTo(originalSeats);
        assertThat(after.getOrganiserId()).isEqualTo(a.id());
    }

    @Test
    void ownerCanUpdateAndDeleteAnEventWithoutBookings() {
        Account a = account(Role.ORGANISER);
        int eventId = createEvent(a);
        String path = "/api/events/" + eventId;
        String newName = "Renamed " + UUID.randomUUID();

        ApiResult put = call(HttpMethod.PUT, path, a.token(), eventBody(newName, 30));
        assertThat(put.status()).isEqualTo(200);
        assertThat(eventRepository.findById(eventId).orElseThrow().getName())
                .isEqualTo(newName);
        assertThat(eventRepository.findById(eventId).orElseThrow().getOrganiserId())
                .isEqualTo(a.id());

        assertThat(call(HttpMethod.DELETE, path, a.token(), null).status())
                .isEqualTo(204);
        assertThat(eventRepository.findById(eventId)).isEmpty();
    }

    @Test
    void eventWithBookingsCannotBeDeletedOrShrunkBelowABookedSeat() {
        Account organiser = account(Role.ORGANISER);
        Account customer = account(Role.CUSTOMER);
        int eventId = createEvent(organiser);
        String path = "/api/events/" + eventId;

        assertThat(book(customer, eventId, customer.email(), List.of(15)).status())
                .isEqualTo(201);

        // 10 seats would remove seat 15 that a customer already holds.
        ApiResult shrink = call(HttpMethod.PUT, path, organiser.token(),
                eventBody("Shrunk " + UUID.randomUUID(), 10));
        assertThat(shrink.status()).isEqualTo(400);
        assertThat(eventRepository.findById(eventId).orElseThrow().getTotalSeats())
                .isEqualTo(20);

        ApiResult delete = call(HttpMethod.DELETE, path, organiser.token(), null);
        assertThat(delete.status()).isEqualTo(409);
        assertThat(delete.body().path("code").asText()).isEqualTo("EVENT_HAS_BOOKINGS");
        assertThat(eventRepository.findById(eventId)).isPresent();
    }

    @Test
    void anotherOrganiserCannotReadAnyPageOfBookings() {
        Account a = account(Role.ORGANISER);
        Account b = account(Role.ORGANISER);
        Account customer = account(Role.CUSTOMER);
        int eventId = createEvent(a);

        assertThat(book(customer, eventId, customer.email(), List.of(1)).status()).isEqualTo(201);
        assertThat(book(customer, eventId, customer.email(), List.of(2)).status()).isEqualTo(201);

        for (int page = 0; page < 3; page++) {
            String path = "/api/events/" + eventId + "/bookings?page=" + page + "&size=1";

            assertThat(get(path, b.token()).status()).as("other organiser page " + page)
                    .isEqualTo(403);
        }

        assertThat(get("/api/events/" + eventId + "/bookings?page=0&size=1", a.token()).status())
                .isEqualTo(200);
    }

    @Test
    void myEventsContainsOnlyTheLoggedInOrganisersEvents() {
        Account a = account(Role.ORGANISER);
        Account b = account(Role.ORGANISER);

        int a1 = createEvent(a);
        int a2 = createEvent(a);
        int b1 = createEvent(b);

        ApiResult mineA = get("/api/events/mine?page=0&size=10", a.token());
        assertThat(mineA.status()).isEqualTo(200);
        assertThat(ids(mineA.body())).containsExactlyInAnyOrder(a1, a2);

        ApiResult mineB = get("/api/events/mine?page=0&size=10", b.token());
        assertThat(ids(mineB.body())).containsExactly(b1);
    }

    // ---- customer isolation -----------------------------------------------

    @Test
    void bookingBelongsToTheLoggedInCustomerEvenWhenBodyNamesSomeoneElse() {
        Account organiser = account(Role.ORGANISER);
        Account c1 = account(Role.CUSTOMER);
        Account c2 = account(Role.CUSTOMER);
        int eventId = createEvent(organiser);

        // Customer 1 puts Customer 2's email (and extra id fields) in the body.
        Map<String, Object> body = new HashMap<>();
        body.put("event_id", eventId);
        body.put("customer_name", "Isolation Customer");
        body.put("customer_email", c2.email());
        body.put("seat_numbers", List.of(1, 2));
        body.put("customerId", c2.id());
        body.put("customer_id", c2.id());

        ApiResult created = post("/api/bookings", c1.token(), body);
        assertThat(created.status()).isIn(201, 400);

        // Whatever happened, Customer 2 owns nothing.
        assertThat(bookingRepository.findAllByCustomerId(c2.id(), PageRequest.of(0, 10)))
                .isEmpty();

        if (created.status() == 201) {
            int bookingId = created.body().path("id").asInt();
            String path = "/api/bookings/" + bookingId;

            assertThat(bookingRepository.findById(bookingId).orElseThrow().getCustomerId())
                    .isEqualTo(c1.id());

            assertThat(get(path, c1.token()).status()).isEqualTo(200);

            // Knowing the booking id and the owner's email grants nothing.
            ApiResult denied = get(path, c2.token());
            assertThat(denied.status()).isEqualTo(403);
            assertThat(denied.body().path("code").asText()).isEqualTo("RESOURCE_ACCESS_DENIED");
        }
    }

    @Test
    void myBookingsContainsOnlyTheLoggedInCustomersBookings() {
        Account organiser = account(Role.ORGANISER);
        Account c1 = account(Role.CUSTOMER);
        Account c2 = account(Role.CUSTOMER);
        int eventId = createEvent(organiser);

        int first = book(c1, eventId, c1.email(), List.of(1)).body().path("id").asInt();
        int second = book(c1, eventId, c1.email(), List.of(2)).body().path("id").asInt();
        int other = book(c2, eventId, c2.email(), List.of(3)).body().path("id").asInt();

        ApiResult mine1 = get("/api/bookings/mine?page=0&size=10", c1.token());
        assertThat(mine1.status()).isEqualTo(200);
        assertThat(ids(mine1.body())).containsExactlyInAnyOrder(first, second);

        ApiResult mine2 = get("/api/bookings/mine?page=0&size=10", c2.token());
        assertThat(ids(mine2.body())).containsExactly(other);
    }

    // ---- data integrity ---------------------------------------------------

    @Test
    void databaseEnforcesOwnershipForeignKeys() {
        Account organiser = account(Role.ORGANISER);
        int eventId = createEvent(organiser);
        Event event = eventRepository.findById(eventId).orElseThrow();

        int unknownUserId = 999_999;

        Event eventWithUnknownOwner = new Event(
                "Orphan Event " + UUID.randomUUID(),
                "Chennai Hall",
                EventCategory.TECH,
                LocalDate.now().plusDays(60),
                20,
                new BigDecimal("1000.00"),
                unknownUserId);

        assertThatThrownBy(() -> eventRepository.saveAndFlush(eventWithUnknownOwner))
                .isInstanceOf(DataIntegrityViolationException.class);

        Booking bookingWithUnknownOwner = new Booking(
                event,
                "Orphan Customer",
                "orphan@example.com",
                "1",
                1,
                BookingStatus.REJECTED,
                "TEST",
                LocalDate.now(),
                unknownUserId);

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(bookingWithUnknownOwner))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
