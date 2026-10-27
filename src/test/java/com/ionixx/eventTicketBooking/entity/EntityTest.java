package com.ionixx.eventticketbooking.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class EntityTest {

    // ------------------------------------------------------------------
    // User
    // ------------------------------------------------------------------

    @Test
    void userExposesConstructorValues() {
        User user = new User("Jane Roe", "jane@example.com", "hash", Role.ORGANISER);

        assertThat(user.getId()).isNull();
        assertThat(user.getName()).isEqualTo("Jane Roe");
        assertThat(user.getEmail()).isEqualTo("jane@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getRole()).isEqualTo(Role.ORGANISER);
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    void userPasswordHashCanBeReplaced() {
        User user = new User("Jane Roe", "jane@example.com", "old", Role.CUSTOMER);

        user.setPasswordHash("new");

        assertThat(user.getPasswordHash()).isEqualTo("new");
    }

    @Test
    void userPrePersistSetsBothTimestamps() {
        User user = new User("Jane Roe", "jane@example.com", "hash", Role.CUSTOMER);
        Instant before = Instant.now();

        user.onCreate();

        assertThat(user.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    void userPreUpdateOnlyMovesUpdatedAt() throws Exception {
        User user = new User("Jane Roe", "jane@example.com", "hash", Role.CUSTOMER);
        user.onCreate();
        Instant created = user.getCreatedAt();
        Thread.sleep(5);

        user.onUpdate();

        assertThat(user.getCreatedAt()).isEqualTo(created);
        assertThat(user.getUpdatedAt()).isAfter(created);
    }

    // ------------------------------------------------------------------
    // Event
    // ------------------------------------------------------------------

    @Test
    void eventExposesConstructorValuesAndLogId() {
        LocalDate date = LocalDate.now().plusDays(10);
        Event event = new Event(
                "Jazz Night", "Marina Hall", EventCategory.MUSIC,
                date, 120, new BigDecimal("750.00"));

        assertThat(event.getId()).isNull();
        assertThat(event.getEventLogId()).isNull();
        assertThat(event.getName()).isEqualTo("Jazz Night");
        assertThat(event.getVenue()).isEqualTo("Marina Hall");
        assertThat(event.getCategory()).isEqualTo(EventCategory.MUSIC);
        assertThat(event.getEventDate()).isEqualTo(date);
        assertThat(event.getTotalSeats()).isEqualTo(120);
        assertThat(event.getBasePrice()).isEqualByComparingTo("750.00");

        event.setEventLogId("ETB0001");
        assertThat(event.getEventLogId()).isEqualTo("ETB0001");
    }

    // ------------------------------------------------------------------
    // Booking
    // ------------------------------------------------------------------

    private static Booking newBooking(Event event, BookingStatus status, String reason) {
        return new Booking(
                event, "Test User", "test@example.com", "1,2", 2,
                status, reason, LocalDate.of(2026, 1, 15));
    }

    @Test
    void bookingExposesConstructorValues() {
        Event event = new Event(
                "Jazz Night", "Marina Hall", EventCategory.MUSIC,
                LocalDate.now().plusDays(10), 120, new BigDecimal("750.00"));

        Booking booking = newBooking(event, BookingStatus.CONFIRMED, null);

        assertThat(booking.getId()).isNull();
        assertThat(booking.getEvent()).isSameAs(event);
        assertThat(booking.getCustomerName()).isEqualTo("Test User");
        assertThat(booking.getCustomerEmail()).isEqualTo("test@example.com");
        assertThat(booking.getSeatNumbers()).isEqualTo("1,2");
        assertThat(booking.getSeats()).isEqualTo(2);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getRejectionReason()).isNull();
        assertThat(booking.getBookedOn()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(booking.getPricePerSeat()).isNull();
        assertThat(booking.getTotalAmount()).isNull();
    }

    @Test
    void confirmSetsPricesAndClearsRejectionReason() {
        Booking booking = newBooking(null, BookingStatus.REJECTED, "Old reason");

        booking.confirm(new BigDecimal("100.00"), new BigDecimal("200.00"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPricePerSeat()).isEqualByComparingTo("100.00");
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(booking.getRejectionReason()).isNull();
    }

    @Test
    void rejectClearsPricesAndStoresReason() {
        Booking booking = newBooking(null, BookingStatus.CONFIRMED, null);
        booking.confirm(new BigDecimal("100.00"), new BigDecimal("200.00"));

        booking.reject("Event is already over");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(booking.getPricePerSeat()).isNull();
        assertThat(booking.getTotalAmount()).isNull();
        assertThat(booking.getRejectionReason()).isEqualTo("Event is already over");
    }

    // ------------------------------------------------------------------
    // PasswordResetToken
    // ------------------------------------------------------------------

    private static PasswordResetToken token(Instant expiresAt) {
        User user = new User("Jane Roe", "jane@example.com", "hash", Role.CUSTOMER);
        return new PasswordResetToken("token-hash", user, expiresAt);
    }

    @Test
    void tokenExposesConstructorValues() {
        Instant expiry = Instant.now().plusSeconds(900);
        Instant before = Instant.now();

        PasswordResetToken token = token(expiry);

        assertThat(token.getId()).isNull();
        assertThat(token.getTokenHash()).isEqualTo("token-hash");
        assertThat(token.getUser().getEmail()).isEqualTo("jane@example.com");
        assertThat(token.getExpiresAt()).isEqualTo(expiry);
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getCreatedAt()).isBetween(before, Instant.now());
    }

    @Test
    void tokenSettersReplaceValues() {
        PasswordResetToken token = token(Instant.now().plusSeconds(900));
        User other = new User("Other Person", "other@example.com", "hash", Role.CUSTOMER);
        Instant newExpiry = Instant.now().plusSeconds(60);
        Instant used = Instant.now();

        token.setTokenHash("another-hash");
        token.setUser(other);
        token.setExpiresAt(newExpiry);
        token.setUsedAt(used);

        assertThat(token.getTokenHash()).isEqualTo("another-hash");
        assertThat(token.getUser()).isSameAs(other);
        assertThat(token.getExpiresAt()).isEqualTo(newExpiry);
        assertThat(token.getUsedAt()).isEqualTo(used);
    }

    @Test
    void tokenIsUsableBeforeExpiryWhenNotYetUsed() {
        Instant now = Instant.now();

        assertThat(token(now.plusSeconds(1)).isUsableAt(now)).isTrue();
    }

    @Test
    void tokenIsNotUsableAtOrAfterExpiry() {
        Instant now = Instant.now();

        assertThat(token(now).isUsableAt(now)).isFalse();
        assertThat(token(now.minusSeconds(1)).isUsableAt(now)).isFalse();
    }

    @Test
    void tokenIsNotUsableOnceMarkedAsUsed() {
        Instant now = Instant.now();
        PasswordResetToken token = token(now.plusSeconds(600));

        token.markAsUsed(now);

        assertThat(token.getUsedAt()).isEqualTo(now);
        assertThat(token.isUsableAt(now)).isFalse();
    }

    // ------------------------------------------------------------------
    // Enums
    // ------------------------------------------------------------------

    @Test
    void enumsExposeTheirDocumentedValues() {
        assertThat(EventCategory.values())
                .containsExactly(EventCategory.MUSIC, EventCategory.SPORTS,
                        EventCategory.TECH, EventCategory.THEATRE);
        assertThat(BookingStatus.values())
                .containsExactly(BookingStatus.CONFIRMED, BookingStatus.REJECTED);
        assertThat(Role.values()).containsExactly(Role.ORGANISER, Role.CUSTOMER);

        assertThat(EventCategory.valueOf("THEATRE")).isEqualTo(EventCategory.THEATRE);
        assertThat(BookingStatus.valueOf("REJECTED")).isEqualTo(BookingStatus.REJECTED);
        assertThat(Role.valueOf("CUSTOMER")).isEqualTo(Role.CUSTOMER);
    }
}