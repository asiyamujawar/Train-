package com.trainconcierge.service;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.StationRequest;
import com.trainconcierge.dto.StationResponse;
import com.trainconcierge.entity.Station;
import com.trainconcierge.exception.ConflictException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.StationRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Station master-data operations. Station codes are always normalised to
 * upper case before they are compared or stored.
 */
@Service
public class StationService {

    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Transactional
    public StationResponse create(StationRequest request) {
        String stationCode = normalizeCode(request.stationCode());
        if (stationRepository.existsByStationCode(stationCode)) {
            throw new ConflictException("Station code '" + stationCode + "' already exists");
        }

        Station station = new Station();
        apply(station, request, stationCode);
        station.setActive(request.active() == null || request.active());

        return StationResponse.from(stationRepository.save(station));
    }

    @Transactional(readOnly = true)
    public StationResponse getById(Long id) {
        return StationResponse.from(findStation(id));
    }

    @Transactional(readOnly = true)
    public StationResponse getByCode(String stationCode) {
        String code = normalizeCode(stationCode);
        return StationResponse.from(findStationByCode(code));
    }

    @Transactional(readOnly = true)
    public PageResponse<StationResponse> search(String name, String city, String stationCode,
                                                Boolean active, Pageable pageable) {
        Page<Station> page = stationRepository.search(
                blankToNull(name),
                blankToNull(city),
                stationCode == null || stationCode.isBlank() ? null : normalizeCode(stationCode),
                active,
                pageable);

        return PageResponse.from(page, StationResponse::from);
    }

    @Transactional
    public StationResponse update(Long id, StationRequest request) {
        Station station = findStation(id);
        String stationCode = normalizeCode(request.stationCode());
        if (stationRepository.existsByStationCodeAndIdNot(stationCode, id)) {
            throw new ConflictException("Station code '" + stationCode + "' already exists");
        }

        apply(station, request, stationCode);
        if (request.active() != null) {
            station.setActive(request.active());
        }

        return StationResponse.from(stationRepository.save(station));
    }

    /** Soft delete: history that references this station stays intact. */
    @Transactional
    public StationResponse deactivate(Long id) {
        Station station = findStation(id);
        station.setActive(false);

        return StationResponse.from(stationRepository.save(station));
    }

    private void apply(Station station, StationRequest request, String stationCode) {
        station.setStationCode(stationCode);
        station.setName(request.name().trim());
        station.setCity(request.city().trim());
        station.setState(blankToNull(request.state()));
    }

    private Station findStation(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + id));
    }

    private Station findStationByCode(String stationCode) {
        return stationRepository.findByStationCode(stationCode)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with code: " + stationCode));
    }

    static String normalizeCode(String stationCode) {
        return stationCode.trim().toUpperCase(Locale.ROOT);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
