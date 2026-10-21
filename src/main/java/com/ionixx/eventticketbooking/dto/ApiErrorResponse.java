
package com.ionixx.eventticketbooking.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldError> errors
) {

    public record FieldError(
            String field,
            Object rejectedValue,
            String message
    ) {
    }
}
