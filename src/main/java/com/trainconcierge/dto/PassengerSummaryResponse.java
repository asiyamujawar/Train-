package com.trainconcierge.dto;

import com.trainconcierge.entity.Passenger;

public record PassengerSummaryResponse(
        Long id,
        String fullName,
        String email,
        boolean active
) {
    public static PassengerSummaryResponse from(Passenger passenger) {
        return new PassengerSummaryResponse(
                passenger.getId(),
                passenger.getFullName(),
                passenger.getEmail(),
                passenger.isActive());
    }
}
