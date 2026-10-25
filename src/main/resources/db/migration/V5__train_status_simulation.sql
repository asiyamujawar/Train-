-- ============================================================================
-- TrainConcierge :: V5 train running status simulation
-- ============================================================================
-- Phase 3 introduces the simulated train running-status tables:
--   * train_statuses          - the CURRENT status of a train for one service date
--   * train_status_history    - an append-only log of every status transition
--
-- Flyway (not Hibernate) owns this schema. `spring.jpa.hibernate.ddl-auto` is
-- "validate", so every table and column below must match the JPA entities in
-- com.trainconcierge.entity exactly.
--
-- This migration is additive only: it never drops or alters existing objects and
-- never touches Phase 1 / Phase 2 data.
--
-- Design decision (CANCELLED): a cancellation is not a delay, so delay_minutes
-- is stored as 0 (never NULL) for CANCELLED, keeping the column NOT NULL and the
-- aggregate arithmetic simple.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- train_statuses (current status, one row per train + service date)
-- ---------------------------------------------------------------------------
CREATE TABLE train_statuses (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    train_id              BIGINT       NOT NULL,
    service_date          DATE         NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    delay_minutes         INT          NOT NULL DEFAULT 0,
    current_station_id    BIGINT       NULL,
    expected_departure_time TIME       NULL,
    expected_arrival_time   TIME       NULL,
    reason                VARCHAR(255) NULL,
    last_updated_at       DATETIME(6)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_train_statuses_train_date UNIQUE (train_id, service_date),
    CONSTRAINT fk_train_statuses_train
        FOREIGN KEY (train_id) REFERENCES trains (id) ON DELETE CASCADE,
    CONSTRAINT fk_train_statuses_station
        FOREIGN KEY (current_station_id) REFERENCES stations (id),
    INDEX idx_train_statuses_train (train_id),
    INDEX idx_train_statuses_service_date (service_date),
    INDEX idx_train_statuses_status (status),
    INDEX idx_train_statuses_delay (delay_minutes),
    INDEX idx_train_statuses_train_date (train_id, service_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- train_status_history (append-only transition log)
-- ---------------------------------------------------------------------------
-- previous_* columns are NULL only for the very first (initial) record.
-- Rows are never updated or deleted by ordinary status changes.
-- ---------------------------------------------------------------------------
CREATE TABLE train_status_history (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    train_id               BIGINT       NOT NULL,
    service_date           DATE         NOT NULL,
    previous_status        VARCHAR(20)  NULL,
    new_status             VARCHAR(20)  NOT NULL,
    previous_delay_minutes INT          NULL,
    new_delay_minutes      INT          NOT NULL,
    reason                 VARCHAR(255) NULL,
    current_station_id     BIGINT       NULL,
    source                 VARCHAR(20)  NOT NULL,
    changed_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_train_status_history_train
        FOREIGN KEY (train_id) REFERENCES trains (id) ON DELETE CASCADE,
    CONSTRAINT fk_train_status_history_station
        FOREIGN KEY (current_station_id) REFERENCES stations (id),
    INDEX idx_train_status_history_train_date (train_id, service_date, changed_at),
    INDEX idx_train_status_history_new_status (new_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
