package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.ionixx.eventticketbooking.entity.Role;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Pagination of event bookings and "my bookings", plus a guard against N+1 queries.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BookingPaginationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String organiserToken;
    private String otherOrganiserToken;
    private String customerOneToken;
    private String customerTwoToken;
    private int eventId;
    private final List<Integer> customerOneBookingIds = new ArrayList<>();

    private void prepare() {
        organiserToken = tokenFor(Role.ORGANISER);
        otherOrganiserToken = tokenFor(Role.ORGANISER);
        customerOneToken = tokenFor(Role.CUSTOMER);
        customerTwoToken = tokenFor(Role.CUSTOMER);

        eventId = createEvent();

        // 12 bookings of one seat each. A customer may hold at most 6 seats
        // per event, so two customers make 6 bookings each.
        for (int seat = 1; seat <= 6; seat++) {
            customerOneBookingIds.add(book(customerOneToken, seat));
        }
        for (int seat = 7; seat <= 12; seat++) {
            book(customerTwoToken, seat);
        }
    }

    private int createEvent() {
        ApiResult result = post("/api/events", organiserToken, Map.of(
                "name", "Pagination Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 30,
                "basePrice", 1500.00));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    private int book(String token, int seat) {
        ApiResult result = post("/api/bookings", token, Map.of(
                "event_id", eventId,
                "customer_name", "Page Customer",
                "customer_email", "page-" + UUID.randomUUID() + "@example.com",
                "seat_numbers", List.of(seat)));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    private JsonNode bookingsPage(String query, String token, int expectedStatus) {
        ApiResult result = get("/api/events/" + eventId + "/bookings" + query, token);
        assertThat(result.status()).as(query).isEqualTo(expectedStatus);
        return result.body();
    }

    @Test
    void pagesAreSplitSortedNewestFirstAndOutOfRangePagesAreEmpty() {
        prepare();

        JsonNode page0 = bookingsPage("?page=0&size=5", organiserToken, 200);
        JsonNode page1 = bookingsPage("?page=1&size=5", organiserToken, 200);
        JsonNode page2 = bookingsPage("?page=2&size=5", organiserToken, 200);
        JsonNode page3 = bookingsPage("?page=3&size=5", organiserToken, 200);

        assertThat(page0.path("content").size()).isEqualTo(5);
        assertThat(page1.path("content").size()).isEqualTo(5);
        assertThat(page2.path("content").size()).isEqualTo(2);
        assertThat(page3.path("content").size()).isZero();
        assertThat(page0.path("totalElements").asLong()).isEqualTo(12);

        // Newest first: ids strictly fall inside a page and across pages.
        int previous = Integer.MAX_VALUE;
        for (JsonNode page : List.of(page0, page1, page2)) {
            for (JsonNode booking : page.path("content")) {
                int id = booking.path("id").asInt();
                assertThat(id).isLessThan(previous);
                previous = id;
            }
        }
    }

    @Test
    void statusFilterIsAppliedBeforePaginationAndEmptyEventsReturnAnEmptyPage() {
        prepare();

        JsonNode confirmed = bookingsPage("?status=CONFIRMED&page=0&size=10",
                organiserToken, 200);
        assertThat(confirmed.path("content").size()).isEqualTo(10);
        assertThat(confirmed.path("totalElements").asLong()).isEqualTo(12);

        JsonNode rejected = bookingsPage("?status=REJECTED&page=0&size=10",
                organiserToken, 200);
        assertThat(rejected.path("content").isEmpty()).isTrue();

        int emptyEventId = createEvent();
        ApiResult empty = get("/api/events/" + emptyEventId + "/bookings?page=0&size=10",
                organiserToken);
        assertThat(empty.status()).isEqualTo(200);
        assertThat(empty.body().path("content").isEmpty()).isTrue();
    }

    @Test
    void invalidPagingParametersAreRejectedWith400() {
        prepare();

        bookingsPage("?page=-1&size=5", organiserToken, 400);
        bookingsPage("?page=0&size=0", organiserToken, 400);
        bookingsPage("?page=0&size=101", organiserToken, 400);
        bookingsPage("?page=abc&size=5", organiserToken, 400);
        bookingsPage("?status=BOGUS", organiserToken, 400);

        assertThat(get("/api/bookings/mine?page=0&size=0", customerOneToken).status())
                .isEqualTo(400);
        assertThat(get("/api/bookings/mine?status=BOGUS", customerOneToken).status())
                .isEqualTo(400);
    }

    @Test
    void ownershipIsEnforcedOnEveryPage() {
        prepare();

        for (int page = 0; page < 4; page++) {
            bookingsPage("?page=" + page + "&size=5", otherOrganiserToken, 403);
        }
    }

    @Test
    void myBookingsIsPaginatedAndContainsOnlyTheCustomersOwnBookings() {
        prepare();

        ApiResult first = get("/api/bookings/mine?page=0&size=4", customerOneToken);
        ApiResult second = get("/api/bookings/mine?page=1&size=4", customerOneToken);

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().path("content").size()).isEqualTo(4);
        assertThat(second.body().path("content").size()).isEqualTo(2);
        assertThat(first.body().path("totalElements").asLong()).isEqualTo(6);

        Set<Integer> expected = Set.copyOf(customerOneBookingIds);
        for (JsonNode booking : first.body().path("content")) {
            assertThat(expected).contains(booking.path("id").asInt());
        }
        for (JsonNode booking : second.body().path("content")) {
            assertThat(expected).contains(booking.path("id").asInt());
        }
    }

    @Test
    void loadingAPageOfBookingsDoesNotRunOneQueryPerBooking() {
        prepare();

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        statistics.clear();
        assertThat(get("/api/events/" + eventId + "/bookings?page=0&size=2",
                organiserToken).status()).isEqualTo(200);
        long statementsForTwoRows = statistics.getPrepareStatementCount();

        statistics.clear();
        assertThat(get("/api/events/" + eventId + "/bookings?page=0&size=12",
                organiserToken).status()).isEqualTo(200);
        long statementsForTwelveRows = statistics.getPrepareStatementCount();

        // Event lookup + page query (+ count query). With an N+1 problem the
        // second number would grow by about one statement per extra booking.
        assertThat(statementsForTwoRows).isLessThanOrEqualTo(4);
        assertThat(statementsForTwelveRows).isLessThanOrEqualTo(4);
    }
}
