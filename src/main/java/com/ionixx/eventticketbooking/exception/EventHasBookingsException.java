package com.ionixx.eventticketbooking.exception;

/**
 * Thrown when an organiser tries to delete an event that already has bookings.
 * The GlobalExceptionHandler converts it into HTTP 409 Conflict.
 */
public class EventHasBookingsException extends RuntimeException {

    public EventHasBookingsException(Integer eventId) {
        super("Event " + eventId + " already has bookings and cannot be deleted");
    }
}
