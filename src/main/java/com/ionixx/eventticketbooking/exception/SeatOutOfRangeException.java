package com.ionixx.eventticketbooking.exception;

public class SeatOutOfRangeException extends RuntimeException {

    private final Integer seatNumber;

    public SeatOutOfRangeException(Integer seatNumber) {
        super("seat " + seatNumber + " does not exist for this event");
        this.seatNumber = seatNumber;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }
}