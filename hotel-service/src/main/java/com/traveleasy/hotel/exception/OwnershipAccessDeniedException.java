package com.traveleasy.hotel.exception;

public class OwnershipAccessDeniedException extends RuntimeException {
    public OwnershipAccessDeniedException(String message) {
        super(message);
    }
}
