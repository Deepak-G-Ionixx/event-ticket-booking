package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;

class AccessRulesTest {

    private static UserPrincipal user(Role role, int id) {
        return new UserPrincipal(id, "user" + id + "@example.com", null, role);
    }

    private static Event eventOwnedBy(Integer organiserId) {
        return new Event(
                "Rock Night", "Chennai Hall", EventCategory.MUSIC,
                LocalDate.now().plusDays(30), 50, new BigDecimal("500.00"),
                organiserId);
    }

    private static Booking bookingBy(Integer customerId, Event event) {
        return new Booking(
                event, "Test User", "test@example.com", "1,2", 2,
                BookingStatus.CONFIRMED, null, LocalDate.now(), customerId);
    }

    // ---- requireCanViewEvent ------------------------------------------------

    @Test
    void customerCanViewAnyEvent() {
        assertThatCode(() -> AccessRules.requireCanViewEvent(
                user(Role.CUSTOMER, 1), eventOwnedBy(99)))
                .doesNotThrowAnyException();
    }

    @Test
    void customerCanViewEventWithoutOwner() {
        assertThatCode(() -> AccessRules.requireCanViewEvent(
                user(Role.CUSTOMER, 1), eventOwnedBy(null)))
                .doesNotThrowAnyException();
    }

    @Test
    void organiserCanViewOwnEvent() {
        assertThatCode(() -> AccessRules.requireCanViewEvent(
                user(Role.ORGANISER, 5), eventOwnedBy(5)))
                .doesNotThrowAnyException();
    }

    @Test
    void organiserCannotViewAnotherOrganisersEvent() {
        assertThatThrownBy(() -> AccessRules.requireCanViewEvent(
                user(Role.ORGANISER, 5), eventOwnedBy(6)))
                .isInstanceOf(ResourceAccessDeniedException.class)
                .hasMessage("You are not allowed to access this resource");
    }

    @Test
    void organiserCannotViewEventWithoutOwner() {
        assertThatThrownBy(() -> AccessRules.requireCanViewEvent(
                user(Role.ORGANISER, 5), eventOwnedBy(null)))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    // ---- requireEventOwner --------------------------------------------------

    @Test
    void ownerOrganiserPassesOwnerCheck() {
        assertThatCode(() -> AccessRules.requireEventOwner(
                user(Role.ORGANISER, 5), eventOwnedBy(5)))
                .doesNotThrowAnyException();
    }

    @Test
    void otherOrganiserFailsOwnerCheck() {
        assertThatThrownBy(() -> AccessRules.requireEventOwner(
                user(Role.ORGANISER, 5), eventOwnedBy(6)))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void customerFailsOwnerCheckEvenWhenIdsMatch() {
        // A customer whose user id equals the organiser id must still be refused.
        assertThatThrownBy(() -> AccessRules.requireEventOwner(
                user(Role.CUSTOMER, 5), eventOwnedBy(5)))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void ownerCheckFailsWhenEventHasNoOwner() {
        assertThatThrownBy(() -> AccessRules.requireEventOwner(
                user(Role.ORGANISER, 5), eventOwnedBy(null)))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    // ---- requireCanViewBooking ---------------------------------------------

    @Test
    void customerCanViewOwnBooking() {
        Booking booking = bookingBy(10, eventOwnedBy(5));

        assertThatCode(() -> AccessRules.requireCanViewBooking(
                user(Role.CUSTOMER, 10), booking))
                .doesNotThrowAnyException();
    }

    @Test
    void customerCannotViewAnotherCustomersBooking() {
        Booking booking = bookingBy(10, eventOwnedBy(5));

        assertThatThrownBy(() -> AccessRules.requireCanViewBooking(
                user(Role.CUSTOMER, 11), booking))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void customerCannotViewBookingWithoutOwner() {
        Booking booking = bookingBy(null, eventOwnedBy(5));

        assertThatThrownBy(() -> AccessRules.requireCanViewBooking(
                user(Role.CUSTOMER, 10), booking))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void organiserCanViewBookingOfOwnEvent() {
        Booking booking = bookingBy(10, eventOwnedBy(5));

        assertThatCode(() -> AccessRules.requireCanViewBooking(
                user(Role.ORGANISER, 5), booking))
                .doesNotThrowAnyException();
    }

    @Test
    void organiserCannotViewBookingOfAnotherOrganisersEvent() {
        Booking booking = bookingBy(10, eventOwnedBy(5));

        assertThatThrownBy(() -> AccessRules.requireCanViewBooking(
                user(Role.ORGANISER, 6), booking))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }
}
