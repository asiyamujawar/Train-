-- V4__passenger_journey_data.sql

-- Passengers table
CREATE TABLE passengers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_passengers_email UNIQUE (email),
    INDEX idx_passengers_email (email),
    INDEX idx_passengers_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Journeys table
CREATE TABLE journeys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    passenger_id BIGINT NOT NULL,
    journey_reference VARCHAR(50) NOT NULL,
    journey_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_journeys_reference UNIQUE (journey_reference),
    CONSTRAINT fk_journeys_passenger FOREIGN KEY (passenger_id) REFERENCES passengers(id),
    INDEX idx_journeys_reference (journey_reference),
    INDEX idx_journeys_passenger_date (passenger_id, journey_date),
    INDEX idx_journeys_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Journey Legs table
CREATE TABLE journey_legs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    journey_id BIGINT NOT NULL,
    train_id BIGINT NOT NULL,
    source_station_id BIGINT NOT NULL,
    destination_station_id BIGINT NOT NULL,
    leg_sequence INT NOT NULL,
    departure_time TIME,
    arrival_time TIME,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_journey_legs_journey FOREIGN KEY (journey_id) REFERENCES journeys(id) ON DELETE CASCADE,
    CONSTRAINT fk_journey_legs_train FOREIGN KEY (train_id) REFERENCES trains(id),
    CONSTRAINT fk_journey_legs_source FOREIGN KEY (source_station_id) REFERENCES stations(id),
    CONSTRAINT fk_journey_legs_destination FOREIGN KEY (destination_station_id) REFERENCES stations(id),
    CONSTRAINT uk_journey_legs_sequence UNIQUE (journey_id, leg_sequence),
    INDEX idx_journey_legs_journey (journey_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
