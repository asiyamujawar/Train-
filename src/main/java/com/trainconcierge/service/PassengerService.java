package com.trainconcierge.service;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.PassengerRequest;
import com.trainconcierge.dto.PassengerResponse;
import com.trainconcierge.entity.Passenger;
import com.trainconcierge.exception.ConflictException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.PassengerRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassengerService {

    private final PassengerRepository passengerRepository;

    public PassengerService(PassengerRepository passengerRepository) {
        this.passengerRepository = passengerRepository;
    }

    @Transactional
    public PassengerResponse create(PassengerRequest request) {
        String email = normalizeEmail(request.email());
        if (passengerRepository.existsByEmail(email)) {
            throw new ConflictException("Passenger with email '" + email + "' already exists");
        }

        Passenger passenger = new Passenger();
        apply(passenger, request, email);
        passenger.setActive(request.active() == null || request.active());

        return PassengerResponse.from(passengerRepository.save(passenger));
    }

    @Transactional(readOnly = true)
    public PassengerResponse getById(Long id) {
        return PassengerResponse.from(findPassenger(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PassengerResponse> search(String fullName, String email,
                                                   Boolean active, Pageable pageable) {
        Page<Passenger> page = passengerRepository.search(
                blankToNull(fullName),
                blankToNull(email),
                active,
                pageable);

        return PageResponse.from(page, PassengerResponse::from);
    }

    @Transactional
    public PassengerResponse update(Long id, PassengerRequest request) {
        Passenger passenger = findPassenger(id);
        String email = normalizeEmail(request.email());
        if (passengerRepository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("Passenger with email '" + email + "' already exists");
        }

        apply(passenger, request, email);
        if (request.active() != null) {
            passenger.setActive(request.active());
        }

        return PassengerResponse.from(passengerRepository.save(passenger));
    }

    @Transactional
    public PassengerResponse deactivate(Long id) {
        Passenger passenger = findPassenger(id);
        passenger.setActive(false);
        return PassengerResponse.from(passengerRepository.save(passenger));
    }

    private void apply(Passenger passenger, PassengerRequest request, String email) {
        passenger.setFullName(request.fullName().trim());
        passenger.setEmail(email);
        passenger.setPhoneNumber(blankToNull(request.phoneNumber()));
    }

    private Passenger findPassenger(Long id) {
        return passengerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with id: " + id));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
