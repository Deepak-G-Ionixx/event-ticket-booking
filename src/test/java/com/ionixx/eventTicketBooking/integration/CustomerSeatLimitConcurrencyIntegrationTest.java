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

/**
 * One customer sends three bookings of three DIFFERENT seats at the same
 * moment. Without serialisation all three could read "0 seats booked" and be
 * confirmed (9 seats). Because BookingService locks the customer's row, the
 * requests run one after another: exactly two are confirmed (6 seats) and the
 * third is rejected with CUSTOMER_SEAT_LIMIT_EXCEEDED.
 */
class CustomerSeatLimitConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void parallelRequestsFromOneCustomerCannotExceedTheSeatLimit() throws Exception {

        String organiserToken = tokenFor(Role.ORGANISER);
        int eventId = createEvent(organiserToken);

        String customerEmail = createUser(Role.CUSTOMER);
        String customerToken = loginAndGetToken(customerEmail, PASSWORD);
        int customerId = userRepository.findByEmail(customerEmail).orElseThrow().getId();

        List<List<Integer>> seatGroups = List.of(
                List.of(1, 2, 3),
                List.of(4, 5, 6),
                List.of(7, 8, 9));

        CountDownLatch ready = new CountDownLatch(seatGroups.size());
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(seatGroups.size());

        List<ApiResult> results = new ArrayList<>();

        try {
            List<Future<ApiResult>> futures = new ArrayList<>();

            for (List<Integer> seats : seatGroups) {
                Callable<ApiResult> task = () -> {
                    ready.countDown();
                    go.await();
                    return post("/api/bookings", customerToken, Map.of(
                            "event_id", eventId,
                            "customer_name", "Limit Customer",
                            "seat_numbers", seats));
                };

                futures.add(pool.submit(task));
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            for (Future<ApiResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).allSatisfy(
                result -> assertThat(result.status()).isEqualTo(201));

        long confirmed = results.stream()
                .filter(r -> "CONFIRMED".equals(r.body().path("status").asText()))
                .count();

        List<String> rejectionReasons = results.stream()
                .filter(r -> "REJECTED".equals(r.body().path("status").asText()))
                .map(r -> r.body().path("rejection_reason").asText())
                .toList();

        assertThat(confirmed).isEqualTo(2);
        assertThat(rejectionReasons)
                .containsExactly("CUSTOMER_SEAT_LIMIT_EXCEEDED");

        // The database agrees: the customer holds exactly the limit.
        assertThat(bookingRepository.countConfirmedSeatsForCustomerId(
                eventId, customerId, BookingStatus.CONFIRMED))
                .isEqualTo(6L);
    }

    private int createEvent(String organiserToken) {
        ApiResult result = post("/api/events", organiserToken, Map.of(
                "name", "Limit Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }
}
