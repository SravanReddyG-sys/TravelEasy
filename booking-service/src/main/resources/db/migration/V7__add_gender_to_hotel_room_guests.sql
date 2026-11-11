-- WP-HB-UI-03: Backend Gender Enhancement
-- Adds nullable gender column to hotel_room_guests for ADULT occupants
-- while preserving null gender for CHILD records and historical reservations.

ALTER TABLE hotel_room_guests
    ADD COLUMN gender VARCHAR(30) NULL AFTER age;
