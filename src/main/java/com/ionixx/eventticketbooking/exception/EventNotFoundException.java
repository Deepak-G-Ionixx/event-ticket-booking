package com.ionixx.eventticketbooking.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Integer id) {
        super("Event not found with id: " + id);
    }
}
