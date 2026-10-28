package com.ionixx.eventticketbooking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ionixx.eventticketbooking.controller.AuthController.MessageResponse;
import com.ionixx.eventticketbooking.dto.ForgotPasswordRequest;
import com.ionixx.eventticketbooking.dto.LoginRequest;
import com.ionixx.eventticketbooking.dto.LoginResponse;
import com.ionixx.eventticketbooking.dto.RegisterRequest;
import com.ionixx.eventticketbooking.dto.RegisterResponse;
import com.ionixx.eventticketbooking.dto.ResetPasswordRequest;
import com.ionixx.eventticketbooking.service.AuthService;
import com.ionixx.eventticketbooking.service.ForgotPasswordService;
import com.ionixx.eventticketbooking.service.ResetPasswordService;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final ForgotPasswordService forgotPasswordService = mock(ForgotPasswordService.class);
    private final ResetPasswordService resetPasswordService = mock(ResetPasswordService.class);

    private final AuthController controller =
            new AuthController(authService, forgotPasswordService, resetPasswordService);

    @Test
    void registerReturns201WithCreatedAccount() {
        RegisterRequest request =
                new RegisterRequest("John Doe", "john@example.com", "password123");
        RegisterResponse response = new RegisterResponse(
                1, "John Doe", "john@example.com", "CUSTOMER", "created");
        when(authService.register(request)).thenReturn(response);

        ResponseEntity<RegisterResponse> result = controller.register(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void loginReturns200WithToken() {
        LoginRequest request = new LoginRequest("john@example.com", "password123");
        LoginResponse response = new LoginResponse(
                "jwt", "Bearer", 3600L, 1, "john@example.com", "CUSTOMER");
        when(authService.login(request)).thenReturn(response);

        ResponseEntity<LoginResponse> result = controller.login(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void forgotPasswordAlwaysReturnsTheSameGenericMessage() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("Anyone@Example.com");

        ResponseEntity<MessageResponse> result = controller.forgotPassword(request);

        verify(forgotPasswordService).requestPasswordReset("anyone@example.com");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message()).isEqualTo(
                "If an eligible account exists for this email, "
                        + "password-reset instructions will be sent.");
    }

    @Test
    void resetPasswordDelegatesToServiceAndConfirms() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "NewPassw0rd!");

        ResponseEntity<MessageResponse> result = controller.resetPassword(request);

        verify(resetPasswordService).resetPassword("raw-token", "NewPassw0rd!");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(
                new MessageResponse("Password has been reset successfully."));
    }
}
