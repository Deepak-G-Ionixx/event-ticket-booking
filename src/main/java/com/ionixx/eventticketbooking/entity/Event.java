package com.ionixx.eventticketbooking.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(
        name = "event",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_name_event_date",
                        columnNames = {"name", "event_date"}
                ),//This means the combination of name and event_date must be unique.
                @UniqueConstraint(
                        name = "uk_event_event_log_id",
                        columnNames = {"event_log_id"}
                )//event_log_id value must be unique across the table.
        }
)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "event_log_id", unique = true, length = 32)
    private String eventLogId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    protected Event() {
        // Required by JPA.
    }

    public Event(
        String name,
        String venue,
        EventCategory category,
        LocalDate eventDate,
        Integer totalSeats,
        BigDecimal basePrice
    ) {
        this.name = name;
        this.venue = venue;
        this.category = category;
        this.eventDate = eventDate;
        this.totalSeats = totalSeats;
        this.basePrice = basePrice;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVenue() {
        return venue;
    }

    public EventCategory getCategory() {
        return category;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public Integer getTotalSeats() {
        return totalSeats;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public String getEventLogId() {
        return eventLogId;
    }

    public void setEventLogId(String eventLogId) {
        this.eventLogId = eventLogId;
    }
}
