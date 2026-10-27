
package com.ionixx.eventticketbooking.service;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.entity.PasswordResetToken;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.repository.PasswordResetTokenRepository;
import com.ionixx.eventticketbooking.repository.UserRepository;

/**
 * Validates password-reset tokens and changes customer passwords.
 */
@Service
public class ResetPasswordService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordResetTokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public ResetPasswordService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordResetTokenService tokenService,
            PasswordEncoder passwordEncoder) {

        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Resets a customer's password if the token is valid.
     *
     * The transaction keeps the password update and token consumption
     * together: both succeed, or neither change is committed.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {

        // Hash the submitted token before looking it up in the database.
        String tokenHash = tokenService.hashToken(rawToken);

        // Lock the matching row to protect against concurrent token use.
        PasswordResetToken resetToken = tokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The password-reset token is invalid or expired"));

        // Reject expired tokens and tokens that have already been consumed.
        if (!resetToken.isUsableAt(Instant.now())) {
            throw new IllegalArgumentException(
                    "The password-reset token is invalid or expired");
        }

        User user = resetToken.getUser();

        // Only customer accounts can use this password-reset flow.
        if (user.getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException(
                    "The password-reset token is invalid or expired");
        }

        // Never save a plain-text password.
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        // Mark the token as consumed so it cannot be used again.
        resetToken.markAsUsed(Instant.now());

        // Save both changes within the same transaction.
        userRepository.save(user);
        tokenRepository.save(resetToken);
    }
}
