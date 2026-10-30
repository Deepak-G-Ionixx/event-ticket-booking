package com.ionixx.eventticketbooking.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ionixx.eventticketbooking.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {

    // Emails are stored lower-case, so callers must pass a lower-case value.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Loads a user and locks the row (SELECT ... FOR UPDATE) until the
     * surrounding transaction ends.
     *
     * Used when a customer books tickets: parallel bookings by the same
     * customer are serialised, so the per-customer seat limit holds even
     * under concurrent requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Integer id);
}
