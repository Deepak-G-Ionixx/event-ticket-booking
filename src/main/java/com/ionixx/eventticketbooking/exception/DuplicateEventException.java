package com.ionixx.eventticketbooking.exception;

public class DuplicateEventException extends RuntimeException {

    public DuplicateEventException() {
        super("An event with the same name and date already exists");
    }
}
