package com.ionixx.eventticketbooking.exception;

public class InvalidBookingRequestException extends RuntimeException {

    private final String field;

    public InvalidBookingRequestException(
            String field,
            String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}