package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.BookingSeatRepository;

/**
 * Many customers try to reserve the SAME seat at the same moment against a
 * real PostgreSQL database. Exactly one reservation may succeed, and the
 * losers must not leave anything behind (no CONFIRMED booking without a seat).
 */
class ConcurrentBookingIntegrationTest extends AbstractIntegrationTest {

    private static final int COMPETING_CUSTOMERS = 8;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Test
    void exactlyOneOfManyConcurrentRequestsReservesTheSeat() throws Exception {

        String organiserToken = tokenFor(Role.ORGANISER);
        int eventId = createEvent(organiserToken);

        // Log every customer in BEFORE the race so the race is only about booking.
        List<String> customerTokens = new ArrayList<>();
        for (int i = 0; i < COMPETING_CUSTOMERS; i++) {
            customerTokens.add(tokenFor(Role.CUSTOMER));
        }

        CountDownLatch ready = new CountDownLatch(COMPETING_CUSTOMERS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(COMPETING_CUSTOMERS);

        List<Future<ApiResult>> futures = new ArrayList<>();

        try {
            for (String token : customerTokens) {
                Callable<ApiResult> task = () -> {
                    ready.countDown();
                    go.await();
                    return post("/api/bookings", token, Map.of(
                            "event_id", eventId,
                            "customer_name", "Race Customer",
                            "customer_email", "race-" + UUID.randomUUID() + "@example.com",
                            "seat_numbers", List.of(5)));
                };
                futures.add(pool.submit(task));
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<ApiResult> results = new ArrayList<>();
            for (Future<ApiResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }

            long confirmed = results.stream()
                    .filter(r -> r.status() == 201
                            && "CONFIRMED".equals(r.body().path("status").asText()))
                    .count();

            // A loser either hit the unique constraint (409) or arrived after the
            // winner committed and was recorded as REJECTED (201). Never a 5xx.
            long acceptableLosers = results.stream()
                    .filter(r -> r.status() == 409
                            || (r.status() == 201
                            && "REJECTED".equals(r.body().path("status").asText())))
                    .count();

            assertThat(confirmed).as("exactly one winner").isEqualTo(1);
            assertThat(acceptableLosers).isEqualTo(COMPETING_CUSTOMERS - 1);

        } finally {
            pool.shutdownNow();
        }

        // The database agrees: one seat row, one CONFIRMED booking. A second
        // CONFIRMED booking here would mean a failed request was not rolled back.
        assertThat(bookingSeatRepository.findConfirmedSeatNumbersByEventId(eventId))
                .containsExactly(5);

        assertThat(bookingRepository.findAllByEvent_IdAndStatus(
                eventId, BookingStatus.CONFIRMED))
                .hasSize(1);
    }

    private int createEvent(String organiserToken) {
        ApiResult result = post("/api/events", organiserToken, Map.of(
                "name", "Concurrency Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }
}
