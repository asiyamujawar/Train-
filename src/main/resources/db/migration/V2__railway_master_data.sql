-- ============================================================================
-- TrainConcierge :: V2 railway master data
-- ============================================================================
-- Phase 1 introduces the railway master-data tables:
--   * stations    - railway stations
--   * trains      - trains running between a source and a destination station
--   * train_stops - the ordered stops (timetable) of a train
--
-- Flyway (not Hibernate) owns this schema. `spring.jpa.hibernate.ddl-auto` is
-- "validate", so every table and column below must match the JPA entities in
-- com.trainconcierge.entity exactly.
--
-- This migration is additive only: it never drops or alters existing objects.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- stations
-- ---------------------------------------------------------------------------
CREATE TABLE stations (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    station_code VARCHAR(10)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    city         VARCHAR(80)  NOT NULL,
    state        VARCHAR(80)  NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_stations_station_code UNIQUE (station_code),
    INDEX idx_stations_name (name),
    INDEX idx_stations_city (city),
    INDEX idx_stations_active (active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- trains
-- ---------------------------------------------------------------------------
-- train_number is a VARCHAR (not a number) so that leading zeros are preserved.
-- Foreign keys use the default RESTRICT behaviour: a station that is still
-- referenced by a train cannot be deleted, which protects historical data.
-- ---------------------------------------------------------------------------
CREATE TABLE trains (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    train_number           VARCHAR(10)  NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    train_type             VARCHAR(30)  NULL,
    source_station_id      BIGINT       NOT NULL,
    destination_station_id BIGINT       NOT NULL,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_trains_train_number UNIQUE (train_number),
    CONSTRAINT fk_trains_source_station
        FOREIGN KEY (source_station_id) REFERENCES stations (id),
    CONSTRAINT fk_trains_destination_station
        FOREIGN KEY (destination_station_id) REFERENCES stations (id),
    INDEX idx_trains_name (name),
    INDEX idx_trains_train_type (train_type),
    INDEX idx_trains_source_station (source_station_id),
    INDEX idx_trains_destination_station (destination_station_id),
    INDEX idx_trains_active (active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
-- train_stops
-- ---------------------------------------------------------------------------
-- One row per station at which a train stops, ordered by stop_sequence.
--   * uk_train_stops_train_sequence -> a train cannot repeat a stop sequence
--   * uk_train_stops_train_station  -> a train cannot list the same station twice
-- Deleting a train cascades to its stops; a station referenced by a stop is
-- protected by the default RESTRICT behaviour.
-- ---------------------------------------------------------------------------
CREATE TABLE train_stops (
    id                     BIGINT NOT NULL AUTO_INCREMENT,
    train_id               BIGINT NOT NULL,
    station_id             BIGINT NOT NULL,
    stop_sequence          INT    NOT NULL,
    arrival_time           TIME   NULL,
    departure_time         TIME   NULL,
    day_offset             INT    NOT NULL DEFAULT 0,
    distance_from_source_km INT   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_train_stops_train_sequence UNIQUE (train_id, stop_sequence),
    CONSTRAINT uk_train_stops_train_station UNIQUE (train_id, station_id),
    CONSTRAINT fk_train_stops_train
        FOREIGN KEY (train_id) REFERENCES trains (id) ON DELETE CASCADE,
    CONSTRAINT fk_train_stops_station
        FOREIGN KEY (station_id) REFERENCES stations (id),
    INDEX idx_train_stops_station (station_id),
    INDEX idx_train_stops_train_sequence (train_id, stop_sequence)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
