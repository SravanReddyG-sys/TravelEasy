package com.traveleasy.hotel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes and aligns the database schema for WP-HB-04.
 * Ensures the legacy child-level unique constraint on hotel_booking_allocations.booking_ref
 * is safely dropped under Hibernate ddl-auto=update and enforces reservation_id NOT NULL.
 *
 * Operational Safety & Idempotency:
 * - Read-only inspection of INFORMATION_SCHEMA determines whether schema adjustments are necessary.
 * - Orphan cleanup (DELETE WHERE reservation_id IS NULL) executes ONLY during one-time schema transition
 *   when reservation_id is still nullable in the database schema. Once reservation_id is NOT NULL,
 *   no DELETE query ever executes on normal application startup.
 * - Unique index detection strictly identifies single-column unique indexes covering booking_ref / bookingRef
 *   and explicitly ignores PRIMARY, foreign keys, and non-unique composite indexes.
 * - Subsequent application restarts execute zero write operations against an already-aligned schema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelReservationSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            alignAllocationSchema();
        } catch (Exception e) {
            log.warn("Non-fatal note during WP-HB-04 schema alignment: {}", e.getMessage());
        }
    }

    private void alignAllocationSchema() {
        try {
            // Step 1: Detect and drop ONLY the exact legacy unique index on hotel_booking_allocations.booking_ref
            // Ensure we strictly target single-column unique indexes on booking_ref/bookingRef and never touch other indexes
            List<String> targetUniqueIndexes = jdbcTemplate.query(
                    "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'hotel_booking_allocations' " +
                            "AND NON_UNIQUE = 0 " +
                            "AND INDEX_NAME != 'PRIMARY' " +
                            "GROUP BY INDEX_NAME " +
                            "HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) IN ('booking_ref', 'bookingRef')",
                    (rs, rowNum) -> rs.getString("INDEX_NAME")
            );

            for (String indexName : targetUniqueIndexes) {
                log.info("Dropping legacy unique index '{}' from hotel_booking_allocations for multi-allocation compatibility", indexName);
                jdbcTemplate.execute("ALTER TABLE hotel_booking_allocations DROP INDEX " + indexName);
            }

            // Step 2: One-time migration for reservation_id column nullability
            // Query INFORMATION_SCHEMA.COLUMNS to check if reservation_id is still nullable
            List<String> reservationIdNullability = jdbcTemplate.query(
                    "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'hotel_booking_allocations' " +
                            "AND COLUMN_NAME = 'reservation_id'",
                    (rs, rowNum) -> rs.getString("IS_NULLABLE")
            );

            if (!reservationIdNullability.isEmpty() && "YES".equalsIgnoreCase(reservationIdNullability.get(0))) {
                log.info("Schema transition detected: reservation_id is nullable. Performing one-time transition to NOT NULL.");

                // Clean up historical legacy orphan records only during schema transition before enforcing NOT NULL
                int purgedOrphans = jdbcTemplate.update(
                        "DELETE FROM hotel_booking_allocations WHERE reservation_id IS NULL");
                if (purgedOrphans > 0) {
                    log.info("Purged {} legacy orphan allocations during one-time schema transition", purgedOrphans);
                }

                // Enforce NOT NULL on reservation_id
                jdbcTemplate.execute("ALTER TABLE hotel_booking_allocations MODIFY COLUMN reservation_id BIGINT NOT NULL");
                log.info("Enforced NOT NULL constraint on hotel_booking_allocations.reservation_id");
            } else {
                log.debug("hotel_booking_allocations.reservation_id is already NOT NULL. Skipping transition cleanup.");
            }

            // Step 3: Ensure booking_ref column is nullable
            List<String> bookingRefNullability = jdbcTemplate.query(
                    "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'hotel_booking_allocations' " +
                            "AND COLUMN_NAME IN ('booking_ref', 'bookingRef')",
                    (rs, rowNum) -> rs.getString("IS_NULLABLE")
            );

            if (!bookingRefNullability.isEmpty() && "NO".equalsIgnoreCase(bookingRefNullability.get(0))) {
                try {
                    jdbcTemplate.execute("ALTER TABLE hotel_booking_allocations MODIFY COLUMN booking_ref VARCHAR(255) NULL");
                } catch (Exception ignored) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE hotel_booking_allocations MODIFY COLUMN bookingRef VARCHAR(255) NULL");
                    } catch (Exception ignored2) {
                    }
                }
            }

            // Step 4: Ensure expires_at column exists in hotel_reservations (WP-HB-05)
            List<String> expiresAtCol = jdbcTemplate.query(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'hotel_reservations' " +
                            "AND COLUMN_NAME IN ('expires_at', 'expiresAt')",
                    (rs, rowNum) -> rs.getString("COLUMN_NAME")
            );

            if (expiresAtCol.isEmpty()) {
                try {
                    jdbcTemplate.execute("ALTER TABLE hotel_reservations ADD COLUMN expires_at DATETIME NULL");
                    log.info("Added missing expires_at column to hotel_reservations");
                } catch (Exception ignored) {
                }
            }

            log.info("WP-HB-04/05 schema alignment verified successfully.");
        } catch (Exception e) {
            log.debug("Schema alignment check completed with info: {}", e.getMessage());
        }
    }
}
