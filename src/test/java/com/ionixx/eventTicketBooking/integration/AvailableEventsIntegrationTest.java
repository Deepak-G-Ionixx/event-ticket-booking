package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ionixx.eventticketbooking.entity.Role;

import tools.jackson.databind.JsonNode;

/**
 * GET /api/events: the customer's list of upcoming events with their free seats.
 */
class AvailableEventsIntegrationTest extends AbstractIntegrationTest {

    private int createEvent(String organiserToken, String name, int daysAhead, int seats) {
        ApiResult result = post("/api/events", organiserToken, Map.of(
                "name", name,
                "venue", "Chennai Test Hall",
                "category", "MUSIC",
                "eventDate", LocalDate.now().plusDays(daysAhead).toString(),
                "totalSeats", seats,
                "basePrice", 800.00));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    private void book(String customerToken, int eventId, List<Integer> seats) {
        ApiResult result = post("/api/bookings", customerToken, Map.of(
                "event_id", eventId,
                "customer_name", "List Customer",
                "customer_email", "list-" + UUID.randomUUID() + "@example.com",
                "seat_numbers", seats));

        assertThat(result.status()).isEqualTo(201);
    }

    private JsonNode find(JsonNode page, int eventId) {
        for (JsonNode item : page.path("content")) {
            if (item.path("id").asInt() == eventId) {
                return item;
            }
        }
        return null;
    }

    @Test
    void customerSeesEventDetailsFreeSeatCountAndFreeSeatRanges() {
        String organiser = tokenFor(Role.ORGANISER);
        String customer = tokenFor(Role.CUSTOMER);
        int eventId = createEvent(organiser, "Range Show " + UUID.randomUUID(), 30, 20);
        book(customer, eventId, List.of(3, 4, 8));

        ApiResult result = get("/api/events?size=100&sort=id,desc", customer);

        assertThat(result.status()).isEqualTo(200);
        JsonNode event = find(result.body(), eventId);
        assertThat(event).isNotNull();
        assertThat(event.path("venue").asText()).isEqualTo("Chennai Test Hall");
        assertThat(event.path("eventDate").asText())
                .isEqualTo(LocalDate.now().plusDays(30).toString());
        assertThat(event.path("totalSeats").asInt()).isEqualTo(20);
        assertThat(event.path("availableSeats").asInt()).isEqualTo(17);

        List<String> ranges = new ArrayList<>();
        for (JsonNode range : event.path("availableSeatRanges")) {
            ranges.add(range.path("from").asInt() + "-" + range.path("to").asInt());
        }
        assertThat(ranges).containsExactly("1-2", "5-7", "9-20");
    }

    @Test
    void eventsAreOrderedSoonestFirstAndPaginated() {
        String organiser = tokenFor(Role.ORGANISER);
        String customer = tokenFor(Role.CUSTOMER);
        String tag = UUID.randomUUID().toString();
        int later = createEvent(organiser, "Later " + tag, 4000, 10);
        int sooner = createEvent(organiser, "Sooner " + tag, 3999, 10);

        // Default order: dates never go backwards, soonest event first.
        JsonNode byDefault = get("/api/events?size=100", customer).body();
        LocalDate previous = LocalDate.MIN;
        for (JsonNode item : byDefault.path("content")) {
            LocalDate date = LocalDate.parse(item.path("eventDate").asText());
            assertThat(date).isAfterOrEqualTo(previous);
            assertThat(date).isAfterOrEqualTo(LocalDate.now());
            previous = date;
        }

        // The sort parameter reverses it: the later event now comes first.
        JsonNode newestDateFirst =
                get("/api/events?size=100&sort=eventDate,desc", customer).body();
        List<Integer> ids = new ArrayList<>();
        for (JsonNode item : newestDateFirst.path("content")) {
            ids.add(item.path("id").asInt());
        }
        assertThat(ids.indexOf(later)).isGreaterThanOrEqualTo(0);
        assertThat(ids.indexOf(later)).isLessThan(ids.indexOf(sooner));

        JsonNode firstPage = get("/api/events?page=0&size=1", customer).body();
        assertThat(firstPage.path("content").size()).isEqualTo(1);
        assertThat(firstPage.path("size").asInt()).isEqualTo(1);
        assertThat(firstPage.path("totalElements").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode beyond = get("/api/events?page=100000&size=10", customer).body();
        assertThat(beyond.path("content").isEmpty()).isTrue();
    }

    @Test
    void deletedEventsAreNotListed() {
        String organiser = tokenFor(Role.ORGANISER);
        String customer = tokenFor(Role.CUSTOMER);
        int eventId = createEvent(organiser, "Deleted " + UUID.randomUUID(), 45, 10);

        assertThat(find(get("/api/events?size=100&sort=id,desc", customer).body(), eventId)).isNotNull();

        assertThat(call(org.springframework.http.HttpMethod.DELETE,
                "/api/events/" + eventId, organiser, null).status()).isEqualTo(200);

        assertThat(find(get("/api/events?size=100&sort=id,desc", customer).body(), eventId)).isNull();
    }

    @Test
    void invalidPagingIsRejectedAndOnlyCustomersMayCallTheList() {
        String customer = tokenFor(Role.CUSTOMER);

        assertThat(get("/api/events?page=-1", customer).status()).isEqualTo(400);
        assertThat(get("/api/events?size=0", customer).status()).isEqualTo(400);
        assertThat(get("/api/events?size=101", customer).status()).isEqualTo(400);
        assertThat(get("/api/events?sort=organiserId", customer).status()).isEqualTo(400);

        assertThat(get("/api/events", null).status()).isEqualTo(401);
        assertThat(get("/api/events", tokenFor(Role.ORGANISER)).status()).isEqualTo(403);
    }
}
