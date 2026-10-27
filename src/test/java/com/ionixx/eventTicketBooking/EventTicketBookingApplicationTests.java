package com.ionixx.eventticketbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class EventTicketBookingApplicationTests {

    private TimeZone originalTimeZone;

    @BeforeEach
    void rememberTimeZone() {
        originalTimeZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void applicationClassCanBeLoaded() {
        // Database-dependent startup is exercised by the integration tests.
        assertThat(EventTicketBookingApplication.class).isNotNull();
    }

    @Test
    void mainSetsKolkataTimeZoneAndStartsSpring() {
        String[] args = {"--spring.main.banner-mode=off"};

        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            EventTicketBookingApplication.main(args);

            spring.verify(() ->
                    SpringApplication.run(EventTicketBookingApplication.class, args));
        }

        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void applicationCanBeInstantiated() {
        assertThat(new EventTicketBookingApplication()).isNotNull();
    }
}