
package com.ionixx.eventticketbooking.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ionixx.eventticketbooking.entity.PasswordResetToken;
import com.ionixx.eventticketbooking.entity.User;

/**
 * Database operations for password-reset tokens.
 *
 * Spring Data JPA creates the implementation automatically.
 */
public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Integer> {

    /**
     * Finds a token by its SHA-256 hash and locks its database row
     * until the surrounding transaction completes.
     *
     * This helps prevent simultaneous requests from consuming the
     * same reset token more than once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    /**
     * Finds all reset requests associated with a user.
     */
    List<PasswordResetToken> findAllByUser(User user);

    /**
     * Deletes existing reset requests before a replacement is issued.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken token WHERE token.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
