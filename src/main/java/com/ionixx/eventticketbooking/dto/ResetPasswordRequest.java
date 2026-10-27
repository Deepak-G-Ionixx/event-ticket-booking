
package com.ionixx.eventticketbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body used when a customer resets their password.
 *
 * The reset token proves that the customer has a valid password-reset
 * request. The new password will be encoded before it is saved.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "token is required")
        @Size(max = 512, message = "token must not exceed 512 characters")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(
                min = 8,
                max = 72,
                message = "password must contain 8 to 72 characters"
        )
        String newPassword
) {

    /**
     * Prevent the token and new password from appearing in logs
     * if this request object is accidentally logged.
     */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
