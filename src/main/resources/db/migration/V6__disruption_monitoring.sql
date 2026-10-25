-- ============================================================================
-- TrainConcierge :: V6 disruption monitoring
-- ============================================================================
-- Phase 4 introduces automatic disruption detection on top of the simulated
-- running statuses from Phase 3:
--   * disruption_events - one row per detected disruption (delay / cancellation)
--
-- Flyway (not Hibernate) owns this schema. `spring.jpa.hibernate.ddl-auto` is
-- "validate", so every table and column below must match the JPA entity
-- com.trainconcierge.entity.DisruptionEvent exactly.
--
-- Additive only: nothing from Phases 1-3 is dropped or altered.
--
-- Event identity / lifecycle (how duplicates are prevented at the DB level):
--   `active_key` is a derived, denormalised key with the shape
--       T<trainId>:<serviceDate>:<disruptionType>   e.g. T1:2026-11-15:CANCELLATION
--   It is populated ONLY while the event is still ongoing (event_state is
--   ACTIVE or ACKNOWLEDGED) and is set back to NULL when the event is RESOLVED.
--   MySQL allows many NULLs in a UNIQUE index, so:
--       * at most one ongoing event per (train, service_date, disruption_type),
--       * unlimited historical events of the same type (recurrences are allowed).
-- ============================================================================

CREATE TABLE disruption_events (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    train_id               BIGINT       NOT NULL,
    service_date           DATE         NOT NULL,
    disruption_type        VARCHAR(30)  NOT NULL,
    observed_train_status  VARCHAR(20)  NOT NULL,
    delay_minutes          INT          NOT NULL DEFAULT 0,
    reason                 VARCHAR(255) NULL,
    detected_at            DATETIME(6)  NOT NULL,
    last_observed_at       DATETIME(6)  NOT NULL,
    event_state            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    resolved_at            DATETIME(6)  NULL,
    active_key             VARCHAR(120) NULL,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_disruption_events_active_key UNIQUE (active_key),
    CONSTRAINT fk_disruption_events_train
        FOREIGN KEY (train_id) REFERENCES trains (id) ON DELETE CASCADE,
    INDEX idx_disruption_events_train_date (train_id, service_date),
    INDEX idx_disruption_events_type (disruption_type),
    INDEX idx_disruption_events_state (event_state),
    INDEX idx_disruption_events_detected (detected_at),
    INDEX idx_disruption_events_delay (delay_minutes)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
