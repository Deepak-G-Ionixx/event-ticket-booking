
package com.ionixx.eventticketbooking.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

/**
 * Generates secure password-reset tokens and hashes them before storage.
 *
 * The original token is a secret and must not be logged or stored in the
 * database. Only its SHA-256 hash should be persisted.
 */
@Service
public class PasswordResetTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically random token.
     *
     * 32 random bytes provide 256 bits of randomness.
     * URL-safe Base64 encoding makes the token suitable for a reset link.
     */
    public String generateToken() {

        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * Calculates the SHA-256 hash of a token.
     *
     * The resulting 64-character hexadecimal string can be stored in
     * the token_hash VARCHAR(64) database column.
     */
    public String hashToken(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Reset token must not be blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    token.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by Java, so this indicates a JVM problem.
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", exception);
        }
    }
}
