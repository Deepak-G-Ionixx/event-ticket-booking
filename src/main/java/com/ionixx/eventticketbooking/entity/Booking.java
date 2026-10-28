package com.ionixx.eventticketbooking.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "customer_name", nullable = false, length = 50)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "seat_numbers", nullable = false, length = 35)
    private String seatNumbers;

    @Column(nullable = false)
    private Integer seats;

    @Column(name = "price_per_seat", precision = 12, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "booked_on", nullable = false)
    private LocalDate bookedOn;

    protected Booking() {
        // Required by JPA.
    }

    public Booking(
            Event event,
            String customerName,
            String customerEmail,
            String seatNumbers,
            Integer seats,
            BookingStatus status,
            String rejectionReason,
            LocalDate bookedOn) {

        this.event = event;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.seatNumbers = seatNumbers;
        this.seats = seats;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.bookedOn = bookedOn;
    }

    public void confirm(
            BigDecimal pricePerSeat,
            BigDecimal totalAmount) {

        this.status = BookingStatus.CONFIRMED;
        this.pricePerSeat = pricePerSeat;
        this.totalAmount = totalAmount;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        this.status = BookingStatus.REJECTED;
        this.pricePerSeat = null;
        this.totalAmount = null;
        this.rejectionReason = reason;
    }

    public Integer getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getSeatNumbers() {
        return seatNumbers;
    }

    public Integer getSeats() {
        return seats;
    }

    public BigDecimal getPricePerSeat() {
        return pricePerSeat;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDate getBookedOn() {
        return bookedOn;
    }
}
