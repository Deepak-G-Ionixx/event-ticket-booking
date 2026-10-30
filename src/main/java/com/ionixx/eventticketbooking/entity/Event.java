
package com.ionixx.eventticketbooking.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "event",
        uniqueConstraints = {
                // Prevent duplicate events with the same name on the same date.
                @UniqueConstraint(
                        name = "uk_event_name_event_date",
                        columnNames = {"name", "event_date"}
                ),

                // Ensure every event has a unique event log ID.
                @UniqueConstraint(
                        name = "uk_event_event_log_id",
                        columnNames = {"event_log_id"}
                )
        }
)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Identifies the event in application logs, for example ETB0001.
    @Column(name = "event_log_id", unique = true, length = 32)
    private String eventLogId;

    // ID of the user who created and owns this event.
    @Column(name = "organiser_id", nullable = false)
    private Integer organiserId;

    // Event name must not be null and can contain up to 100 characters.
    @Column(nullable = false, length = 100)
    private String name;

    // Venue where the event takes place.
    @Column(nullable = false, length = 100)
    private String venue;

    // Store the enum name, such as MUSIC or SPORTS, in the database.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    // Date on which the event takes place.
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    // Total number of seats available for the event.
    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    // Base ticket price, stored with two decimal places.
    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    // Required by JPA to create entity objects from database records.
    protected Event() {
    }

    /**
     * Backward-compatible constructor for existing tests and legacy code.
     *
     * Note: organiserId remains null when this constructor is used.
     * Use the constructor below when creating real events.
     */
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

    /**
     * Constructor for creating an event with its organiser assigned.
     */
    public Event(
            String name,
            String venue,
            EventCategory category,
            LocalDate eventDate,
            Integer totalSeats,
            BigDecimal basePrice,
            Integer organiserId
    ) {
        this.name = name;
        this.venue = venue;
        this.category = category;
        this.eventDate = eventDate;
        this.totalSeats = totalSeats;
        this.basePrice = basePrice;
        this.organiserId = organiserId;
    }

    /**
     * Replaces the editable details of the event.
     * The owner (organiserId), the id and the event log id never change.
     */
    public void updateDetails(
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

    // Returns the database-generated event ID.
    public Integer getId() {
        return id;
    }

    // Returns the event's unique logging identifier.
    public String getEventLogId() {
        return eventLogId;
    }

    // Sets the event's logging identifier.
    public void setEventLogId(String eventLogId) {
        this.eventLogId = eventLogId;
    }

    // Returns the ID of the organiser who owns this event.
    public Integer getOrganiserId() {
        return organiserId;
    }

    // Returns the event name.
    public String getName() {
        return name;
    }

    // Returns the venue.
    public String getVenue() {
        return venue;
    }

    // Returns the event category.
    public EventCategory getCategory() {
        return category;
    }

    // Returns the event date.
    public LocalDate getEventDate() {
        return eventDate;
    }

    // Returns the total number of seats.
    public Integer getTotalSeats() {
        return totalSeats;
    }

    // Returns the base ticket price.
    public BigDecimal getBasePrice() {
        return basePrice;
    }
}
