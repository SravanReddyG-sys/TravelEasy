-- WP-HB-UI-03: Schema Consistency Correction for V7 Gender Column
-- Aligns SQL migration DDL with the Hibernate 6 / MySQL ENUM convention used by
-- HotelRoomGuest.gender (@Enumerated(EnumType.STRING)) and other Java enums across Booking Service.
-- Non-destructive and idempotent across both V7 (VARCHAR(30) NULL) schemas and
-- Hibernate-managed ENUM('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY') NULL schemas.

ALTER TABLE hotel_room_guests
    MODIFY COLUMN gender ENUM('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY') NULL;
