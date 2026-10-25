-- ============================================================================
-- TrainConcierge :: V3 seed railway sample data
-- ============================================================================
-- Small, deterministic demonstration dataset for Phase 1.
--
-- IMPORTANT: this data is ILLUSTRATIVE ONLY. Station names are real Indian
-- railway stations, but the train numbers, timings and distances are fictional
-- demo values. This is NOT an authoritative or live railway timetable.
--
-- Reproducibility: every statement uses INSERT IGNORE together with the unique
-- constraints defined in V2 (station_code, train_number, and the train-stop
-- composite keys), so running this migration again is a no-op instead of
-- producing duplicates. No existing user data is deleted or reset by the
-- application at startup.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Stations
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO stations (station_code, name, city, state, active) VALUES
    ('CSMT', 'Chhatrapati Shivaji Maharaj Terminus', 'Mumbai', 'Maharashtra', TRUE),
    ('PUNE', 'Pune Junction', 'Pune', 'Maharashtra', TRUE),
    ('SUR', 'Solapur Junction', 'Solapur', 'Maharashtra', TRUE),
    ('SBC', 'KSR Bengaluru City Junction', 'Bengaluru', 'Karnataka', TRUE),
    ('NDLS', 'New Delhi', 'New Delhi', 'Delhi', TRUE),
    ('AGC', 'Agra Cantt', 'Agra', 'Uttar Pradesh', TRUE);
-- 'PUNE' searches below (e.g. PUNE -> SUR) return a matching train, while the
-- reversed order (SUR -> PUNE) returns nothing, which is exactly the behaviour
-- required by the Phase 1 timetable-search rules.

-- ---------------------------------------------------------------------------
-- Trains
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO trains (train_number, name, train_type, source_station_id, destination_station_id, active) VALUES
    ('12951', 'Mumbai Rajdhani Express (demo)', 'RAJDHANI',
        (SELECT id FROM stations WHERE station_code = 'CSMT'),
        (SELECT id FROM stations WHERE station_code = 'NDLS'), TRUE),
    ('12123', 'Deccan Queen (demo)', 'SUPERFAST',
        (SELECT id FROM stations WHERE station_code = 'CSMT'),
        (SELECT id FROM stations WHERE station_code = 'PUNE'), TRUE),
    ('11013', 'Pune-Solapur Intercity (demo)', 'EXPRESS',
        (SELECT id FROM stations WHERE station_code = 'PUNE'),
        (SELECT id FROM stations WHERE station_code = 'SUR'), TRUE),
    ('12627', 'Karnataka Express (demo)', 'SUPERFAST',
        (SELECT id FROM stations WHERE station_code = 'NDLS'),
        (SELECT id FROM stations WHERE station_code = 'SBC'), TRUE);

-- ---------------------------------------------------------------------------
-- Train stops (ordered timetables)
-- ---------------------------------------------------------------------------
-- 12951 CSMT -> SUR -> AGC -> NDLS (overnight, hence day_offset = 1)
INSERT IGNORE INTO train_stops
    (train_id, station_id, stop_sequence, arrival_time, departure_time, day_offset, distance_from_source_km) VALUES
    ((SELECT id FROM trains WHERE train_number = '12951'), (SELECT id FROM stations WHERE station_code = 'CSMT'), 1, NULL,       '17:00:00', 0, 0),
    ((SELECT id FROM trains WHERE train_number = '12951'), (SELECT id FROM stations WHERE station_code = 'SUR'),  2, '23:30:00', '23:40:00', 0, 455),
    ((SELECT id FROM trains WHERE train_number = '12951'), (SELECT id FROM stations WHERE station_code = 'AGC'),  3, '07:00:00', '07:05:00', 1, 1200),
    ((SELECT id FROM trains WHERE train_number = '12951'), (SELECT id FROM stations WHERE station_code = 'NDLS'), 4, '09:30:00', NULL,       1, 1384);

-- 12123 CSMT -> PUNE
INSERT IGNORE INTO train_stops
    (train_id, station_id, stop_sequence, arrival_time, departure_time, day_offset, distance_from_source_km) VALUES
    ((SELECT id FROM trains WHERE train_number = '12123'), (SELECT id FROM stations WHERE station_code = 'CSMT'), 1, NULL,       '07:15:00', 0, 0),
    ((SELECT id FROM trains WHERE train_number = '12123'), (SELECT id FROM stations WHERE station_code = 'PUNE'), 2, '10:25:00', NULL,       0, 192);

-- 11013 PUNE -> SUR
INSERT IGNORE INTO train_stops
    (train_id, station_id, stop_sequence, arrival_time, departure_time, day_offset, distance_from_source_km) VALUES
    ((SELECT id FROM trains WHERE train_number = '11013'), (SELECT id FROM stations WHERE station_code = 'PUNE'), 1, NULL,       '08:00:00', 0, 0),
    ((SELECT id FROM trains WHERE train_number = '11013'), (SELECT id FROM stations WHERE station_code = 'SUR'),  2, '12:30:00', NULL,       0, 264);

-- 12627 NDLS -> AGC -> PUNE -> SBC (two overnight legs, day_offset 1 and 2)
INSERT IGNORE INTO train_stops
    (train_id, station_id, stop_sequence, arrival_time, departure_time, day_offset, distance_from_source_km) VALUES
    ((SELECT id FROM trains WHERE train_number = '12627'), (SELECT id FROM stations WHERE station_code = 'NDLS'), 1, NULL,       '20:30:00', 0, 0),
    ((SELECT id FROM trains WHERE train_number = '12627'), (SELECT id FROM stations WHERE station_code = 'AGC'),  2, '23:00:00', '23:05:00', 0, 195),
    ((SELECT id FROM trains WHERE train_number = '12627'), (SELECT id FROM stations WHERE station_code = 'PUNE'), 3, '14:00:00', '14:10:00', 1, 1740),
    ((SELECT id FROM trains WHERE train_number = '12627'), (SELECT id FROM stations WHERE station_code = 'SBC'),  4, '06:00:00', NULL,       2, 2444);
