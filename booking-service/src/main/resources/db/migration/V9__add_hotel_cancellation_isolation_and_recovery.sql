-- WP-HC-01: Hotel Cancellation Backend Isolation and Durable Recovery
-- Extends bookings for independent inventory release and cancellation actor tracking,
-- and adds durable recovery queues for hotel inventory release and cancellation refunds.

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS inventory_release_status VARCHAR(30) NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN IF NOT EXISTS inventory_released_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS cancelled_by_role VARCHAR(40) NULL;

CREATE TABLE IF NOT EXISTS hotel_inventory_release_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    booking_ref VARCHAR(120) NOT NULL,
    hotel_id BIGINT NULL,
    service_id BIGINT NULL,
    date_wise_booking BOOLEAN NOT NULL DEFAULT TRUE,
    details_json TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 10,
    last_attempt_at DATETIME(6) NULL,
    next_retry_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    locked_by VARCHAR(80) NULL,
    locked_until DATETIME(6) NULL,
    last_error VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_hirt_booking_id UNIQUE (booking_id),
    CONSTRAINT uk_hirt_booking_ref UNIQUE (booking_ref),
    INDEX idx_hirt_status_retry (status, next_retry_at),
    INDEX idx_hirt_lease (status, locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hotel_cancellation_refund_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    booking_ref VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    refund_amount DOUBLE NOT NULL,
    total_booking_amount DOUBLE NOT NULL,
    cancellation_fee DOUBLE NOT NULL,
    policy_tier VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    razorpay_refund_id VARCHAR(100) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 10,
    last_attempt_at DATETIME(6) NULL,
    next_retry_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    locked_by VARCHAR(80) NULL,
    locked_until DATETIME(6) NULL,
    last_error VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_hcrt_booking_id UNIQUE (booking_id),
    CONSTRAINT uk_hcrt_booking_ref UNIQUE (booking_ref),
    CONSTRAINT uk_hcrt_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_hcrt_status_retry (status, next_retry_at),
    INDEX idx_hcrt_lease (status, locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
