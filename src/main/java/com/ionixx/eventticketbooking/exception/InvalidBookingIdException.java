package com.ionixx.eventticketbooking.exception;

public class InvalidBookingIdException extends RuntimeException {

    public InvalidBookingIdException() {
        super("Booking ID must be a positive integer");
    }
}