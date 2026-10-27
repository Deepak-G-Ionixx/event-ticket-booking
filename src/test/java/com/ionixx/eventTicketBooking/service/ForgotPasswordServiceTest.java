package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;

import com.ionixx.eventticketbooking.entity.PasswordResetToken;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.repository.PasswordResetTokenRepository;
import com.ionixx.eventticketbooking.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForgotPasswordServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordResetTokenService tokenService;

    @Mock
    private PasswordResetEmailService emailService;

    private ForgotPasswordService service;

    @BeforeEach
    void setUp() {
        service = new ForgotPasswordService(
                userRepository, tokenRepository, tokenService, emailService);
    }

    private static User user(Role role) {
        return new User("Test User", "cust@example.com", "hash", role);
    }

    @Test
    void unknownEmailDoesNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("nobody@example.com");

        verifyNoInteractions(tokenRepository, tokenService, emailService);
    }

    @Test
    void organiserAccountDoesNothing() {
        when(userRepository.findByEmail("cust@example.com"))
                .thenReturn(Optional.of(user(Role.ORGANISER)));

        service.requestPasswordReset("cust@example.com");

        verifyNoInteractions(tokenRepository, tokenService, emailService);
    }

    @Test
    void customerGetsFreshTokenStoredHashedAndEmailed() {
        User customer = user(Role.CUSTOMER);
        when(userRepository.findByEmail("cust@example.com"))
                .thenReturn(Optional.of(customer));
        when(tokenService.generateToken()).thenReturn("RAW-TOKEN");
        when(tokenService.hashToken("RAW-TOKEN")).thenReturn("HASHED-TOKEN");

        Instant before = Instant.now();
        // Mixed case and surrounding spaces must be normalised before the lookup.
        service.requestPasswordReset("  Cust@Example.COM ");
        Instant after = Instant.now();

        verify(tokenRepository).deleteAllByUser(customer);

        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).saveAndFlush(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertThat(saved.getTokenHash()).isEqualTo("HASHED-TOKEN");
        assertThat(saved.getUser()).isSameAs(customer);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getExpiresAt())
                .isBetween(before.plus(Duration.ofMinutes(15)),
                        after.plus(Duration.ofMinutes(15)));

        // The raw token goes only into the email, never into the database.
        verify(emailService).sendPasswordResetEmail("cust@example.com", "RAW-TOKEN");
        verify(tokenRepository, never()).delete(any(PasswordResetToken.class));
    }

    @Test
    void failedEmailDeliveryCancelsTheResetRequest() {
        User customer = user(Role.CUSTOMER);
        when(userRepository.findByEmail("cust@example.com"))
                .thenReturn(Optional.of(customer));
        when(tokenService.generateToken()).thenReturn("RAW-TOKEN");
        when(tokenService.hashToken("RAW-TOKEN")).thenReturn("HASHED-TOKEN");
        doThrow(new MailSendException("SMTP down"))
                .when(emailService).sendPasswordResetEmail("cust@example.com", "RAW-TOKEN");

        service.requestPasswordReset("cust@example.com");

        ArgumentCaptor<PasswordResetToken> saved =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).saveAndFlush(saved.capture());
        verify(tokenRepository).delete(saved.getValue());
    }
}