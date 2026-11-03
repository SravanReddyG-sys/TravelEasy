package com.traveleasy.notification.model;

/**
 * Role of the notification recipient.
 * Extensible for future roles (HOTEL_PARTNER, ADMIN).
 */
public enum RecipientRole {
    TRAVELLER,
    BUS_OPERATOR,
    HOTEL_PARTNER,
    ADMIN
}
