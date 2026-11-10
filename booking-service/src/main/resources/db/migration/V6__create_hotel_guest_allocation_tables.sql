-- WP-HB-06.1: Backend Guest Allocation Foundation
-- Establishes structured primary guest contact on reservation snapshots,
-- physical room-level allocations, and individual adult/child room occupants.

-- 1. Extend parent reservation snapshot with primary guest columns
ALTER TABLE hotel_reservation_snapshots
    ADD COLUMN primary_guest_name VARCHAR(255) NULL,
    ADD COLUMN primary_guest_email VARCHAR(255) NULL,
    ADD COLUMN primary_guest_mobile VARCHAR(50) NULL;

-- 2. Create physical-room guest allocation table
CREATE TABLE IF NOT EXISTS hotel_room_guest_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    allocation_snapshot_id BIGINT NOT NULL,
    room_sequence INT NOT NULL,
    adult_count INT NOT NULL DEFAULT 1,
    child_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_hrga_allocation_snapshot_id (allocation_snapshot_id),
    CONSTRAINT uk_hrga_allocation_sequence UNIQUE (allocation_snapshot_id, room_sequence),
    CONSTRAINT fk_hrga_allocation_snapshot
        FOREIGN KEY (allocation_snapshot_id)
        REFERENCES hotel_allocation_snapshots(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Create individual room guest occupant table
CREATE TABLE IF NOT EXISTS hotel_room_guests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_guest_allocation_id BIGINT NOT NULL,
    guest_name VARCHAR(255) NOT NULL,
    guest_type VARCHAR(20) NOT NULL,
    age INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_hrg_room_guest_allocation_id (room_guest_allocation_id),
    CONSTRAINT fk_hrg_room_guest_allocation
        FOREIGN KEY (room_guest_allocation_id)
        REFERENCES hotel_room_guest_allocations(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
