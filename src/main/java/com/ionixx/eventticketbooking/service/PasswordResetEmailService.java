
package com.ionixx.eventticketbooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends password-reset instructions to customers.
 *
 * The original token is included only in the reset link sent to the
 * customer's email. It must never be logged or stored in plain text.
 */
@Service
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String resetBaseUrl;

    public PasswordResetEmailService(
            JavaMailSender mailSender,
            @Value("${app.security.password-reset.from}") String fromAddress,
            @Value("${app.security.password-reset.base-url}") String resetBaseUrl) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.resetBaseUrl = resetBaseUrl;
    }

    /**
     * Sends a reset link to the customer's registered email address.
     */
    public void sendPasswordResetEmail(String email, String token) {

        // Encode the token so it can safely be placed in a URL.
        String encodedToken =
                java.net.URLEncoder.encode(
                        token,
                        java.nio.charset.StandardCharsets.UTF_8);

        String resetLink = resetBaseUrl
                + (resetBaseUrl.contains("?") ? "&" : "?")
                + "token=" + encodedToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Reset your Event Ticket Booking password");
        message.setText(
                "We received a request to reset your password.\n\n"
                        + "Use the link below to choose a new password:\n"
                        + resetLink + "\n\n"
                        + "This link expires in 15 minutes and can only be used once.\n"
                        + "If you did not request this reset, you can ignore this email.");

        mailSender.send(message);
    }
}
