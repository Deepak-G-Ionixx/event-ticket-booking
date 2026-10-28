package com.ionixx.eventticketbooking.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ionixx.eventticketbooking.entity.Event;

public interface EventRepository extends JpaRepository<Event, Integer> {

    boolean existsByNameAndEventDate(String name, LocalDate eventDate);
}
