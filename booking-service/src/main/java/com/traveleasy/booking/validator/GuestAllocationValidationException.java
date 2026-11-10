package com.traveleasy.booking.validator;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

/**
 * Domain validation exception for structured guest allocation rules (WP-HB-06.1).
 * Maps directly to HTTP 400 Bad Request with field-level validation details.
 */
public class GuestAllocationValidationException extends ResponseStatusException {

    private final List<String> validationErrors;

    public GuestAllocationValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.validationErrors = Collections.singletonList(message);
    }

    public GuestAllocationValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, field + ": " + message);
        this.validationErrors = Collections.singletonList(field + ": " + message);
    }

    public GuestAllocationValidationException(List<String> validationErrors) {
        super(HttpStatus.BAD_REQUEST, validationErrors != null && !validationErrors.isEmpty()
                ? String.join("; ", validationErrors)
                : "Guest allocation validation failed");
        this.validationErrors = validationErrors != null ? Collections.unmodifiableList(validationErrors) : Collections.emptyList();
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
