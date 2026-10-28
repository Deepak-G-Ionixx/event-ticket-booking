package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class PasswordResetEmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private SimpleMailMessage sendWith(String baseUrl, String token) {
        PasswordResetEmailService service = new PasswordResetEmailService(
                mailSender, "no-reply@example.com", baseUrl);

        service.sendPasswordResetEmail("cust@example.com", token);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void emailIsAddressedFromConfiguredSenderToCustomer() {
        SimpleMailMessage message =
                sendWith("http://localhost:3000/reset-password", "abc123");

        assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
        assertThat(message.getTo()).containsExactly("cust@example.com");
        assertThat(message.getSubject())
                .isEqualTo("Reset your Event Ticket Booking password");
        assertThat(message.getText())
                .contains("expires in 15 minutes")
                .contains("can only be used once");
    }

    @Test
    void linkUsesQuestionMarkWhenBaseUrlHasNoQueryString() {
        SimpleMailMessage message =
                sendWith("http://localhost:3000/reset-password", "abc123");

        assertThat(message.getText())
                .contains("http://localhost:3000/reset-password?token=abc123");
    }

    @Test
    void linkUsesAmpersandWhenBaseUrlAlreadyHasQueryString() {
        SimpleMailMessage message =
                sendWith("http://localhost:3000/reset?lang=en", "abc123");

        assertThat(message.getText())
                .contains("http://localhost:3000/reset?lang=en&token=abc123");
    }

    @Test
    void tokenIsUrlEncodedInTheLink() {
        SimpleMailMessage message =
                sendWith("http://localhost:3000/reset-password", "a+b/c=d e");

        assertThat(message.getText()).contains("token=a%2Bb%2Fc%3Dd+e");
    }
}
