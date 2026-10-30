package com.ionixx.eventticketbooking.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class OwnershipEntityTest {

    private static Event event(Integer organiserId) {
        return new Event(
                "Rock Night", "Chennai Hall", EventCategory.MUSIC,
                LocalDate.now().plusDays(30), 50, new BigDecimal("500.00"),
                organiserId);
    }

    @Test
    void eventWithoutOrganiserHasNoOwner() {
        Event event = new Event(
                "Rock Night", "Chennai Hall", EventCategory.MUSIC,
                LocalDate.now().plusDays(30), 50, new BigDecimal("500.00"));

        assertThat(event.getOrganiserId()).isNull();
    }

    @Test
    void eventRemembersItsOrganiser() {
        assertThat(event(8).getOrganiserId()).isEqualTo(8);
    }

    @Test
    void bookingWithoutCustomerHasNoOwner() {
        Booking booking = new Booking(
                event(8), "Test User", "test@example.com", "1", 1,
                BookingStatus.CONFIRMED, null, LocalDate.now());

        assertThat(booking.getCustomerId()).isNull();
    }

    @Test
    void bookingRemembersItsCustomer() {
        Booking booking = new Booking(
                event(8), "Test User", "test@example.com", "1", 1,
                BookingStatus.CONFIRMED, null, LocalDate.now(), 15);

        assertThat(booking.getCustomerId()).isEqualTo(15);
        assertThat(booking.getEvent().getOrganiserId()).isEqualTo(8);
    }
}