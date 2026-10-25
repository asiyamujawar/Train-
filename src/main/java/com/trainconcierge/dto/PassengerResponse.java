package com.trainconcierge.dto;

import com.trainconcierge.entity.Passenger;
import java.time.OffsetDateTime;

public record PassengerResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PassengerResponse from(Passenger passenger) {
        return new PassengerResponse(
                passenger.getId(),
                passenger.getFullName(),
                passenger.getEmail(),
                passenger.getPhoneNumber(),
                passenger.isActive(),
                passenger.getCreatedAt(),
                passenger.getUpdatedAt());
    }
}
