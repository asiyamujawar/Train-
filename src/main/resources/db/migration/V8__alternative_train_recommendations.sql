-- ============================================================================
-- TrainConcierge :: V8 alternative train recommendations
-- ============================================================================
-- Phase 6 turns the journey impacts detected in Phase 5 into concrete,
-- deterministic alternative-train suggestions:
--   * train_recommendations - one row per (journey, leg, impact, candidate train)
--
-- Flyway (not Hibernate) owns this schema. `spring.jpa.hibernate.ddl-auto` is
-- "validate", so every table and column below must match the JPA entity
-- com.trainconcierge.entity.TrainRecommendation exactly.
--
-- Additive only: nothing from Phases 1-5 is dropped or altered.
--
-- Recommendation identity / lifecycle (how duplicates are prevented):
--   `active_key` is a derived, denormalised key with the shape
--       J<journeyId>:L<legId|0>:I<impactId>:T<candidateTrainId>
--   e.g. J7:L12:I9:T4
--   Leg 0 is the sentinel used when the impact is not attached to one specific
--   leg. It is populated ONLY while the recommendation is AVAILABLE and is set
--   back to NULL once the recommendation is EXPIRED. MySQL allows many NULLs in
--   a UNIQUE index, so:
--       * at most one AVAILABLE recommendation exists per (journey, leg, impact,
--         candidate train),
--       * an impact keeps an unlimited, append-only history of recommendations
--         that were suggested in the past.
--
-- A recommendation is a suggestion only: nothing in this table ever modifies the
-- passenger's journey.
-- ============================================================================

CREATE TABLE train_recommendations (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    journey_id              BIGINT       NOT NULL,
    journey_leg_id          BIGINT       NULL,
    journey_impact_id       BIGINT       NOT NULL,
    disruption_event_id     BIGINT       NULL,
    impact_type             VARCHAR(30)  NOT NULL,
    train_id                BIGINT       NOT NULL,
    service_date            DATE         NOT NULL,
    departure_time          TIME         NULL,
    departure_day_offset    INT          NULL,
    arrival_time            TIME         NULL,
    arrival_day_offset      INT          NULL,
    transfer_count          INT          NOT NULL DEFAULT 0,
    additional_minutes      INT          NULL,
    score                   BIGINT       NOT NULL,
    recommendation_status   VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    status_known            BOOLEAN      NOT NULL DEFAULT FALSE,
    candidate_status        VARCHAR(20)  NULL,
    candidate_delay_minutes INT          NULL,
    explanation             VARCHAR(500) NOT NULL,
    active_key              VARCHAR(190) NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_train_recommendations_active_key UNIQUE (active_key),
    CONSTRAINT fk_train_recommendations_journey
        FOREIGN KEY (journey_id) REFERENCES journeys (id) ON DELETE CASCADE,
    CONSTRAINT fk_train_recommendations_leg
        FOREIGN KEY (journey_leg_id) REFERENCES journey_legs (id) ON DELETE CASCADE,
    CONSTRAINT fk_train_recommendations_impact
        FOREIGN KEY (journey_impact_id) REFERENCES journey_impacts (id) ON DELETE CASCADE,
    CONSTRAINT fk_train_recommendations_event
        FOREIGN KEY (disruption_event_id) REFERENCES disruption_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_train_recommendations_train
        FOREIGN KEY (train_id) REFERENCES trains (id) ON DELETE CASCADE,
    INDEX idx_train_recommendations_journey (journey_id),
    INDEX idx_train_recommendations_leg (journey_leg_id),
    INDEX idx_train_recommendations_impact (journey_impact_id),
    INDEX idx_train_recommendations_event (disruption_event_id),
    INDEX idx_train_recommendations_train (train_id),
    INDEX idx_train_recommendations_status (recommendation_status),
    INDEX idx_train_recommendations_score (score),
    INDEX idx_train_recommendations_service_date (service_date),
    INDEX idx_train_recommendations_impact_status (journey_impact_id, recommendation_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
