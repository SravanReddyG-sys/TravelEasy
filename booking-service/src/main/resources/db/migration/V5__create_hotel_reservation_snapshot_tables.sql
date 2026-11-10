-- WP-HB-05: Relational Reservation and Allocation Snapshot Persistence
-- Replaces reliance on the 4000-character detailsJson column for multi-room hotel bookings.

CREATE TABLE IF NOT EXISTS hotel_reservation_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    booking_ref VARCHAR(255) NOT NULL,
    hotel_id BIGINT NOT NULL,
    hotel_name VARCHAR(255),
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    total_nights INT,
    total_amount DOUBLE NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'HELD',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_hrs_booking_id (booking_id),
    INDEX idx_hrs_booking_ref (booking_ref),
    INDEX idx_hrs_hotel_id (hotel_id),
    INDEX idx_hrs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hotel_allocation_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_snapshot_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    room_type_name VARCHAR(255),
    quantity INT NOT NULL,
    allocation_total DOUBLE NOT NULL,
    nightly_rates_json TEXT,
    check_in_date DATE,
    check_out_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'HELD',
    guest_allocation_json TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_has_reservation_id (reservation_snapshot_id),
    INDEX idx_has_room_id (room_id),
    INDEX idx_has_status (status),
    CONSTRAINT fk_has_reservation_snapshot
        FOREIGN KEY (reservation_snapshot_id)
        REFERENCES hotel_reservation_snapshots(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
