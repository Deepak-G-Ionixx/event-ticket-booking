
package com.ionixx.eventticketbooking;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventTicketBookingApplication {

    public static void main(String[] args) {

        // Set the application's default timezone to Indian Standard Time.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

        // Start the Spring Boot application.
        SpringApplication.run(EventTicketBookingApplication.class, args);
    }
}
