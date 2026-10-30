package com.ionixx.eventticketbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ionixx.eventticketbooking.entity.Role;

/**
 * Proves that the database itself enforces ownership and seat integrity,
 * independent of the Java code. Rows are written with plain SQL so the
 * service layer cannot hide a missing constraint.
 */
class SchemaIntegrityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpJdbc() {
        jdbc = new JdbcTemplate(dataSource);
    }

    private int createEvent(String organiserToken) {
        ApiResult result = post("/api/events", organiserToken, Map.of(
                "name", "Schema Event " + UUID.randomUUID(),
                "venue", "Chennai Test Hall",
                "category", "TECH",
                "eventDate", LocalDate.now().plusDays(60).toString(),
                "totalSeats", 20,
                "basePrice", 1500.00));

        assertThat(result.status()).isEqualTo(201);
        return result.body().path("id").asInt();
    }

    private String isNullable(String table, String column) {
        return jdbc.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """, String.class, table, column);
    }

    private static String rootMessage(Throwable exception) {
        return ((DataIntegrityViolationException) exception)
                .getMostSpecificCause()
                .getMessage();
    }

    @Test
    void ownershipAndRelationshipColumnsAreNotNull() {
        assertThat(isNullable("event", "organiser_id")).isEqualTo("NO");
        assertThat(isNullable("booking", "customer_id")).isEqualTo("NO");
        assertThat(isNullable("booking", "event_id")).isEqualTo("NO");
        assertThat(isNullable("booking_seat", "event_id")).isEqualTo("NO");
        assertThat(isNullable("booking_seat", "booking_id")).isEqualTo("NO");
    }

    @Test
    void ownershipAndIntegrityMigrationsHaveBeenApplied() {
        List<String> ids = List.of(
                "010-enforce-event-organiser-not-null",
                "010-enforce-booking-customer-not-null",
                "010-booking-list-indexes",
                "011-booking-seat-matches-booking-event",
                "011-event-organiser-date-index");

        Long executed = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM databasechangelog
                WHERE id IN (?, ?, ?, ?, ?)
                  AND exectype = 'EXECUTED'
                """, Long.class, ids.toArray());

        assertThat(executed).isEqualTo(5L);
    }

    @Test
    void eventWithoutOrganiserIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO event (name, venue, category, event_date, total_seats, base_price)
                VALUES (?, 'Chennai Hall', 'TECH', ?, 20, 100.00)
                """, "No Owner " + UUID.randomUUID(), LocalDate.now().plusDays(30)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(rootMessage(e)).contains("organiser_id"));
    }

    @Test
    void bookingWithoutCustomerIsRejectedByTheDatabase() {
        int eventId = createEvent(tokenFor(Role.ORGANISER));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO booking (event_id, customer_name, customer_email,
                                     seat_numbers, seats, status, booked_on)
                VALUES (?, 'No Owner', 'none@example.com', '1', 1, 'REJECTED', ?)
                """, eventId, LocalDate.now()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(rootMessage(e)).contains("customer_id"));
    }

    @Test
    void seatRowsMustBeUniqueAndBelongToTheEventOfTheirBooking() {
        String organiserToken = tokenFor(Role.ORGANISER);
        int firstEvent = createEvent(organiserToken);
        int secondEvent = createEvent(organiserToken);

        ApiResult booking = post("/api/bookings", tokenFor(Role.CUSTOMER), Map.of(
                "event_id", firstEvent,
                "customer_name", "Schema Customer",
                "seat_numbers", List.of(1)));
        assertThat(booking.status()).isEqualTo(201);
        int bookingId = booking.body().path("id").asInt();

        String insertSeat = """
                INSERT INTO booking_seat (event_id, booking_id, seat_number)
                VALUES (?, ?, ?)
                """;

        // Same seat of the same event a second time: unique constraint.
        assertThatThrownBy(() -> jdbc.update(insertSeat, firstEvent, bookingId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A seat of the SECOND event pointing at a booking of the FIRST event:
        // composite foreign key (booking_id, event_id) -> booking (id, event_id).
        assertThatThrownBy(() -> jdbc.update(insertSeat, secondEvent, bookingId, 7))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Nothing extra was stored.
        Long seatRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM booking_seat WHERE booking_id = ?",
                Long.class, bookingId);
        assertThat(seatRows).isEqualTo(1L);
    }
}
