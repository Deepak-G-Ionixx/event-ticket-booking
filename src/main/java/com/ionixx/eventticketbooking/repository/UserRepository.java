package com.ionixx.eventticketbooking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ionixx.eventticketbooking.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {

    // Emails are stored lower-case, so callers must pass a lower-case value.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}