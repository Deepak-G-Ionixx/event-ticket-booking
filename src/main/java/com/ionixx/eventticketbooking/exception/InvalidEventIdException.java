package com.ionixx.eventticketbooking.exception;

public class InvalidEventIdException extends RuntimeException {

    public InvalidEventIdException() {
        super("Event ID must be a positive integer");
    }
}
