-- ============================================================================
-- TrainConcierge :: V9 hotel recommendations
-- ============================================================================
-- Phase 7 introduces the hotel domain:
--   * hotels                - hotel master data linked (optionally) to a station
--   * hotel_recommendations - the persisted outcome of one hotel evaluation run
--
-- Flyway (not Hibernate) owns this schema. `spring.jpa.hibernate.ddl-auto` is
-- "validate", so every table and column below must match the JPA entities in
-- com.trainconcierge.entity exactly.
--
-- This migration is additive only: it never drops or alters existing objects.
--
-- IMPORTANT: no hotel rows are inserted here. Demo hotels are seeded at runtime
-- by a development-only, property-gated component (see HotelDemoDataSeeder), so
-- that fictional data can never reach a production database through a migration.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- hotels
-- ---------------------------------------------------------------------------
-- A hotel is *optionally* linked to a station: station_id is nullable because a
-- hotel may be described by a locality only. Distance is stored in kilometres
-- relative to that linked station and is never derived or fabricated.
--
--   * price_per_night / distance_km / rating / currency are all NULLable: missing
--     optional data is allowed and is never replaced with an invented value.
--   * availability_status distinguishes seeded illustrative demo data
--     ('DEMO_DATA') from hotels whose availability was never verified
--     ('AVAILABILITY_UNKNOWN'). There is deliberately no 'CONFIRMED' value -
--     TrainConcierge has no booking or availability integration.
-- ---------------------------------------------------------------------------
CREATE TABLE hotels (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    name                VARCHAR(150)   NOT NULL,
    station_id          BIGINT         NULL,
    locality            VARCHAR(150)   NULL,
    distance_km         DECIMAL(6, 2)  NULL,
    price_per_night     DECIMAL(10, 2) NULL,
    currency            VARCHAR(3)     NULL,
    rating              DECIMAL(2, 1)  NULL,
    availability_status VARCHAR(30)    NOT NULL DEFAULT 'AVAILABILITY_UNKNOWN',
    amenities           VARCHAR(255)   NULL,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_hotels_station
        FOREIGN KEY (station_id) REFERENCES stations (id) ON DELETE SET NULL,
    INDEX idx_hotels_name (name),
    INDEX idx_hotels_station (station_id),
    INDEX idx_hotels_currency (currency),
    INDEX idx_hotels_price (price_per_night),
    INDEX idx_hotels_rating (rating),
    INDEX idx_hotels_active (active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- hotel_recommendations
-- ---------------------------------------------------------------------------
-- Historical record of one hotel evaluation, one row per recommended hotel.
--
--   * active_key is a derived uniqueness key present only while the row is
--     AVAILABLE and NULL once it is EXPIRED. It backs the database-level
--     "one AVAILABLE row per (journey, hotel)" guarantee: MySQL allows many
--     NULLs in a UNIQUE index, so history is preserved while duplicates within
--     the current result set are impossible.
-- ---------------------------------------------------------------------------
CREATE TABLE hotel_recommendations (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    journey_id          BIGINT       NOT NULL,
    journey_impact_id   BIGINT       NULL,
    hotel_id            BIGINT       NOT NULL,
    decision_outcome    VARCHAR(30)  NOT NULL,
    relevant_station_id BIGINT       NULL,
    rank_position       INT          NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    recommendation_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    active_key          VARCHAR(190) NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_hotel_recommendations_active_key UNIQUE (active_key),
    CONSTRAINT fk_hotel_recommendations_journey
        FOREIGN KEY (journey_id) REFERENCES journeys (id) ON DELETE CASCADE,
    CONSTRAINT fk_hotel_recommendations_impact
        FOREIGN KEY (journey_impact_id) REFERENCES journey_impacts (id) ON DELETE CASCADE,
    CONSTRAINT fk_hotel_recommendations_hotel
        FOREIGN KEY (hotel_id) REFERENCES hotels (id) ON DELETE CASCADE,
    CONSTRAINT fk_hotel_recommendations_station
        FOREIGN KEY (relevant_station_id) REFERENCES stations (id) ON DELETE SET NULL,
    INDEX idx_hotel_recommendations_journey (journey_id),
    INDEX idx_hotel_recommendations_impact (journey_impact_id),
    INDEX idx_hotel_recommendations_hotel (hotel_id),
    INDEX idx_hotel_recommendations_status (recommendation_status),
    INDEX idx_hotel_recommendations_journey_status (journey_id, recommendation_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
