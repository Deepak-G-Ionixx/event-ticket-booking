
package com.ionixx.eventticketbooking.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.entity.PasswordResetToken;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.repository.PasswordResetTokenRepository;
import com.ionixx.eventticketbooking.repository.UserRepository;

/**
 * Coordinates password-reset requests for customer accounts.
 *
 * The API should return the same general response whether the email exists
 * or not, so callers cannot discover registered accounts.
 */
@Service
public class ForgotPasswordService {

    private static final Logger log =
            LoggerFactory.getLogger(ForgotPasswordService.class);

    private static final long TOKEN_VALIDITY_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetTokenService tokenService;
    private final PasswordResetEmailService emailService;

    public ForgotPasswordService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetTokenService tokenService,
            PasswordResetEmailService emailService) {

        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
    }

    /**
     * Starts a password reset when the email belongs to a customer.
     *
     * This method deliberately does nothing when the email is unknown
     * or belongs to a non-customer account.
     */
    @Transactional
    public void requestPasswordReset(String email) {

        Optional<User> optionalUser = userRepository.findByEmail(
                email.trim().toLowerCase(java.util.Locale.ROOT));

        if (optionalUser.isEmpty()) {
            // Do not disclose whether the account exists.
            return;
        }

        User user = optionalUser.get();

        if (user.getRole() != Role.CUSTOMER) {
            // Password-reset requests through this flow are for customers only.
            return;
        }

        // Invalidate previous reset requests before issuing a replacement.
        tokenRepository.deleteAllByUser(user);

        // Generate the secret token and hash it before database storage.
        String rawToken = tokenService.generateToken();
        String tokenHash = tokenService.hashToken(rawToken);

        // The token is valid for 15 minutes from creation.
        Instant expiryTime = Instant.now()
                .plus(TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES);

        PasswordResetToken resetToken = new PasswordResetToken(
                tokenHash,
                user,
                expiryTime);

        tokenRepository.saveAndFlush(resetToken);

        try {
            // Only the original token goes into the email link.
            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

        } catch (MailException exception) {
            // Never log the token, reset link, or password.
            // Remove the unusable reset request if email delivery fails.
            tokenRepository.delete(resetToken);

            log.warn(
                    "Password-reset email could not be sent; "
                            + "the reset request was cancelled");
        }
    }
}
