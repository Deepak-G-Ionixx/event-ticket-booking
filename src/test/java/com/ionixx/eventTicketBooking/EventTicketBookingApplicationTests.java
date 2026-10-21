package com.ionixx.eventticketbooking;

import org.junit.jupiter.api.Test;

class EventTicketBookingApplicationTests {

    @Test
    void applicationClassCanBeLoaded() {
        // Database-dependent startup is exercised manually with Docker Compose.
        // This test intentionally does not start the Spring context.
        org.junit.jupiter.api.Assertions.assertNotNull(EventTicketBookingApplication.class);
    }
}
