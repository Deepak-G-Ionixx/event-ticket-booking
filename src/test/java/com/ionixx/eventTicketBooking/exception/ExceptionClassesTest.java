package com.ionixx.eventticketbooking.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExceptionClassesTest {

    @Test
    void notFoundExceptionsIncludeTheId() {
        assertThat(new BookingNotFoundException(12))
                .hasMessage("Booking not found with id: 12");
        assertThat(new EventNotFoundException(34))
                .hasMessage("Event not found with id: 34");
    }

    @Test
    void invalidIdExceptionsExplainTheRule() {
        assertThat(new InvalidBookingIdException())
                .hasMessage("Booking ID must be a positive integer");
        assertThat(new InvalidEventIdException())
                .hasMessage("Event ID must be a positive integer");
    }

    @Test
    void duplicateEventExceptionHasFixedMessage() {
        assertThat(new DuplicateEventException())
                .hasMessage("An event with the same name and date already exists");
    }

    @Test
    void emailAlreadyRegisteredExceptionKeepsMessage() {
        assertThat(new EmailAlreadyRegisteredException("taken")).hasMessage("taken");
    }

    @Test
    void invalidBookingRequestExceptionKeepsFieldAndMessage() {
        InvalidBookingRequestException exception =
                new InvalidBookingRequestException("seat_numbers", "bad seats");

        assertThat(exception.getField()).isEqualTo("seat_numbers");
        assertThat(exception).hasMessage("bad seats");
    }

    @Test
    void seatOutOfRangeExceptionKeepsSeatNumber() {
        SeatOutOfRangeException exception = new SeatOutOfRangeException(42);

        assertThat(exception.getSeatNumber()).isEqualTo(42);
        assertThat(exception).hasMessage("seat 42 does not exist for this event");
    }
}
