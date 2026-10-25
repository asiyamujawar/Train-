package com.trainconcierge.service;

import com.trainconcierge.dto.HotelRequest;
import com.trainconcierge.dto.HotelResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.entity.Hotel;
import com.trainconcierge.entity.HotelAvailabilityStatus;
import com.trainconcierge.entity.Station;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.HotelRepository;
import com.trainconcierge.repository.StationRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hotel master-data operations.
 *
 * <p>Hotel records created here are always {@link HotelAvailabilityStatus#AVAILABILITY_UNKNOWN}:
 * TrainConcierge never stores a claim that a room is available, because it has no
 * booking or live-availability integration.</p>
 */
@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final StationRepository stationRepository;

    public HotelService(HotelRepository hotelRepository, StationRepository stationRepository) {
        this.hotelRepository = hotelRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = new Hotel();
        apply(hotel, request);
        hotel.setAvailabilityStatus(HotelAvailabilityStatus.AVAILABILITY_UNKNOWN);
        hotel.setActive(request.active() == null || request.active());

        return HotelResponse.from(hotelRepository.save(hotel));
    }

    @Transactional(readOnly = true)
    public HotelResponse getById(Long id) {
        return HotelResponse.from(findHotel(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<HotelResponse> search(String name,
                                              Long stationId,
                                              String currency,
                                              Boolean active,
                                              BigDecimal maxPrice,
                                              BigDecimal minRating,
                                              BigDecimal maxDistanceKm,
                                              String amenity,
                                              Pageable pageable) {
        Page<Hotel> page = hotelRepository.search(
                blankToNull(name),
                stationId,
                currency == null || currency.isBlank() ? null : normalizeCurrency(currency),
                active,
                maxPrice,
                minRating,
                maxDistanceKm,
                blankToNull(amenity),
                pageable);

        return PageResponse.from(page, HotelResponse::from);
    }

    private void apply(Hotel hotel, HotelRequest request) {
        hotel.setName(request.name().trim());
        hotel.setStation(resolveStation(request.stationId()));
        hotel.setLocality(blankToNull(request.locality()));
        hotel.setDistanceKm(request.distanceKm());
        hotel.setPricePerNight(request.pricePerNight());
        hotel.setCurrency(normalizeCurrency(request.currency()));
        hotel.setRating(request.rating());
        hotel.setAmenities(normalizeAmenities(request.amenities()));
    }

    private Station resolveStation(Long stationId) {
        if (stationId == null) {
            return null;
        }
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + stationId));
    }

    private Hotel findHotel(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + id));
    }

    static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    /** Upper-cases, trims, de-duplicates and comma-joins the amenity list. */
    static String normalizeAmenities(String amenities) {
        if (amenities == null || amenities.isBlank()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String part : amenities.split(",")) {
            String value = part.trim().toUpperCase(Locale.ROOT);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
