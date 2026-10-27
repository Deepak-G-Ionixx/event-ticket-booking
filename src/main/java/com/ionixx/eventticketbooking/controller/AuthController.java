
package com.ionixx.eventticketbooking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import com.ionixx.eventticketbooking.dto.ResetPasswordRequest;
import com.ionixx.eventticketbooking.service.ResetPasswordService;
import com.ionixx.eventticketbooking.dto.ForgotPasswordRequest;
import com.ionixx.eventticketbooking.dto.LoginRequest;
import com.ionixx.eventticketbooking.dto.LoginResponse;
import com.ionixx.eventticketbooking.dto.RegisterRequest;
import com.ionixx.eventticketbooking.dto.RegisterResponse;
import com.ionixx.eventticketbooking.service.AuthService;
import com.ionixx.eventticketbooking.service.ForgotPasswordService;

import jakarta.validation.Valid;

/**
 * Handles authentication-related HTTP requests:
 * registration, login, and password-reset requests.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final ForgotPasswordService forgotPasswordService;
    private final ResetPasswordService resetPasswordService;

    public AuthController(
            AuthService authService,
            ForgotPasswordService forgotPasswordService,
            ResetPasswordService resetPasswordService) {

        this.authService = authService;
        this.forgotPasswordService = forgotPasswordService;
        this.resetPasswordService = resetPasswordService;
    }

    /**
     * Registers a new customer account.
     *
     * POST /auth/register
     */

    /**
     * Resets a customer's password using a valid reset token.
     *
     * POST /auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        resetPasswordService.resetPassword(
                request.token(),
                request.newPassword());

        return ResponseEntity.ok(
                new MessageResponse(
                        "Password has been reset successfully."));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterResponse response = authService.register(request);

        // Return 201 Created because a new account was created.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Authenticates an existing user and returns an access token.
     *
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Requests a password-reset email for a customer account.
     *
     * POST /auth/forgot-password
     *
     * The response is intentionally generic so callers cannot discover
     * whether a particular email address is registered.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        forgotPasswordService.requestPasswordReset(request.email());

        return ResponseEntity.ok(
                new MessageResponse(
                        "If an eligible account exists for this email, "
                                + "password-reset instructions will be sent."));
    }

    /**
     * Simple response body for endpoints that return a message.
     */
    public record MessageResponse(String message) {
    }
}
