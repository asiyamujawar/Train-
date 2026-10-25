package com.trainconcierge.entity;

/**
 * Optional classification of a train. Stored as a string (VARCHAR) so the
 * database stays readable and new values can be added in later phases without
 * a data migration.
 */
public enum TrainType {
    EXPRESS,
    SUPERFAST,
    PASSENGER,
    MAIL,
    RAJDHANI,
    SHATABDI,
    DURONTO,
    LOCAL
}
