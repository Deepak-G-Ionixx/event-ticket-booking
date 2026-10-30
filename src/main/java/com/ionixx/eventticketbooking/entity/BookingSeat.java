package com.ionixx.eventticketbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Stores one seat reserved by a booking.
 *
 * The database unique constraint prevents the same seat from being
 * reserved twice for the same event, including concurrent requests.
 */
@Entity
@Table(
        name = "booking_seat",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_seat_number",
                        columnNames = {"event_id", "seat_number"}
                )
        }
)
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    protected BookingSeat() {
        // Required by JPA.
    }

    public BookingSeat(Event event, Booking booking, Integer seatNumber) {
        this.event = event;
        this.booking = booking;
        this.seatNumber = seatNumber;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Booking getBooking() {
        return booking;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }
}
