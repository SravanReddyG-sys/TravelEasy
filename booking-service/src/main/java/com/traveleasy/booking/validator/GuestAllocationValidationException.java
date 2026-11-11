package com.traveleasy.booking.validator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

/**
 * Domain validation exception for structured guest allocation rules (WP-HB-06.1, extended WP-HB-UI-03).
 * Maps directly to HTTP 400 Bad Request with field-level validation details.
 */
public class GuestAllocationValidationException extends ResponseStatusException {

    private final List<String> validationErrors;
    private final List<ValidationErrorDetail> errorDetails;

    public GuestAllocationValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.validationErrors = Collections.singletonList(message);
        this.errorDetails = Collections.emptyList();
    }

    public GuestAllocationValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, field + ": " + message);
        this.validationErrors = Collections.singletonList(field + ": " + message);
        this.errorDetails = Collections.singletonList(new ValidationErrorDetail(null, null, null, null, field, message));
    }

    public GuestAllocationValidationException(List<String> validationErrors) {
        super(HttpStatus.BAD_REQUEST, validationErrors != null && !validationErrors.isEmpty()
                ? String.join("; ", validationErrors)
                : "Guest allocation validation failed");
        this.validationErrors = validationErrors != null ? Collections.unmodifiableList(validationErrors) : Collections.emptyList();
        this.errorDetails = Collections.emptyList();
    }

    public GuestAllocationValidationException(List<String> validationErrors, List<ValidationErrorDetail> errorDetails) {
        super(HttpStatus.BAD_REQUEST, validationErrors != null && !validationErrors.isEmpty()
                ? String.join("; ", validationErrors)
                : "Guest allocation validation failed");
        this.validationErrors = validationErrors != null ? Collections.unmodifiableList(validationErrors) : Collections.emptyList();
        this.errorDetails = errorDetails != null ? Collections.unmodifiableList(errorDetails) : Collections.emptyList();
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public List<ValidationErrorDetail> getErrorDetails() {
        return errorDetails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationErrorDetail {
        private Integer allocationIndex;
        private Long roomId;
        private Integer roomSequence;
        private Integer guestIndex;
        private String field;
        private String message;
    }
}
