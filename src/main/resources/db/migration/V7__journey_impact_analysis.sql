-- ============================================================================
-- TrainConcierge :: V7 journey impact analysis
-- ============================================================================
-- Phase 5 links the disruptions detected in Phase 4 to the journeys planned in
-- Phase 2:
--   * journey_impacts - one row per (journey, leg, disruption event, impact type)
--
-- Flyway (not Hibernate) owns this schema. `spring.jpa.hibernate.ddl-auto` is
-- "validate", so every table and column below must match the JPA entity
-- com.trainconcierge.entity.JourneyImpact exactly.
--
-- Additive only: nothing from Phases 1-4 is dropped or altered.
--
-- Impact identity / lifecycle (how duplicates are prevented at the DB level):
--   `active_key` is a derived, denormalised key with the shape
--       J<journeyId>:L<legId|0>:E<eventId>:<IMPACT_TYPE>
--   e.g. J7:L12:E3:MISSED_CONNECTION
--   Leg 0 is the sentinel used for journey-level impacts (ITINERARY_DISRUPTED)
--   that are not attached to one specific leg (journey_leg_id is NULL).
--   It is populated ONLY while the impact is ongoing (impact_state = 'ACTIVE')
--   and is set back to NULL when the impact is RESOLVED. MySQL allows many NULLs
--   in a UNIQUE index, so:
--       * at most one ACTIVE impact per (journey, leg, event, impact type),
--       * unlimited historical impacts of the same type (recurrences allowed).
-- ============================================================================

CREATE TABLE journey_impacts (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    journey_id          BIGINT       NOT NULL,
    journey_leg_id      BIGINT       NULL,
    disruption_event_id BIGINT       NOT NULL,
    impact_type         VARCHAR(30)  NOT NULL,
    severity            VARCHAR(20)  NOT NULL,
    delay_minutes       INT          NULL,
    explanation         VARCHAR(500) NOT NULL,
    detected_at         DATETIME(6)  NOT NULL,
    last_observed_at    DATETIME(6)  NOT NULL,
    impact_state        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    resolved_at         DATETIME(6)  NULL,
    active_key          VARCHAR(160) NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_journey_impacts_active_key UNIQUE (active_key),
    CONSTRAINT fk_journey_impacts_journey
        FOREIGN KEY (journey_id) REFERENCES journeys (id) ON DELETE CASCADE,
    CONSTRAINT fk_journey_impacts_leg
        FOREIGN KEY (journey_leg_id) REFERENCES journey_legs (id) ON DELETE CASCADE,
    CONSTRAINT fk_journey_impacts_event
        FOREIGN KEY (disruption_event_id) REFERENCES disruption_events (id) ON DELETE CASCADE,
    INDEX idx_journey_impacts_journey (journey_id),
    INDEX idx_journey_impacts_leg (journey_leg_id),
    INDEX idx_journey_impacts_event (disruption_event_id),
    INDEX idx_journey_impacts_type (impact_type),
    INDEX idx_journey_impacts_severity (severity),
    INDEX idx_journey_impacts_state (impact_state),
    INDEX idx_journey_impacts_detected (detected_at),
    INDEX idx_journey_impacts_journey_state (journey_id, impact_state)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
