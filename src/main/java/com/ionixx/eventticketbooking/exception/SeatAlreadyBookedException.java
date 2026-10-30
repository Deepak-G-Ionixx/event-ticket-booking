package com.ionixx.eventticketbooking.exception;

/**
 * Raised when a seat has already been reserved by another booking.
 * The API should translate this exception to HTTP 409 Conflict.
 */
public class SeatAlreadyBookedException extends RuntimeException {

    public SeatAlreadyBookedException(String message) {
        super(message);
    }
}