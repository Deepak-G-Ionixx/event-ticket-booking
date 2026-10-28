package com.ionixx.eventticketbooking.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Integer id) {
        super("Booking not found with id: " + id);
    }
}
