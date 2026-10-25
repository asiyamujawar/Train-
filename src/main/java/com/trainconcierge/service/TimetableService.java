package com.trainconcierge.service;

import com.trainconcierge.dto.StationSummaryResponse;
import com.trainconcierge.dto.TimetableResponse;
import com.trainconcierge.dto.TimetableSearchResponse;
import com.trainconcierge.dto.TimetableSearchResult;
import com.trainconcierge.dto.TrainResponse;
import com.trainconcierge.dto.TrainStopResponse;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.exception.InvalidRequestException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStopRepository;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only timetable operations: a single train's ordered timetable and the
 * source/destination search across the network.
 *
 * <p>The search never claims real-time running status or seat availability; it
 * only reports scheduled times that exist in the master data.</p>
 */
@Service
public class TimetableService {

    private final TrainRepository trainRepository;
    private final TrainStopRepository trainStopRepository;
    private final StationRepository stationRepository;

    public TimetableService(TrainRepository trainRepository,
                            TrainStopRepository trainStopRepository,
                            StationRepository stationRepository) {
        this.trainRepository = trainRepository;
        this.trainStopRepository = trainStopRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional(readOnly = true)
    public TimetableResponse getTimetable(Long trainId) {
        Train train = trainRepository.findDetailById(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found with id: " + trainId));

        List<TrainStopResponse> stops = trainStopRepository.findStopsWithStation(trainId).stream()
                .map(stop -> TrainStopResponse.from(stop, trainId))
                .toList();

        return new TimetableResponse(TrainResponse.from(train), stops);
    }

    /**
     * Finds active trains stopping at both stations in the requested order.
     * A train visiting the destination before the source is never returned.
     */
    @Transactional(readOnly = true)
    public TimetableSearchResponse search(String sourceStationCode, String destinationStationCode) {
        String sourceCode = StationService.normalizeCode(sourceStationCode);
        String destinationCode = StationService.normalizeCode(destinationStationCode);

        Station source = findStation(sourceCode);
        Station destination = findStation(destinationCode);

        if (source.getId().equals(destination.getId())) {
            throw new InvalidRequestException("Source and destination stations must be different");
        }
        if (!source.isActive() || !destination.isActive()) {
            throw new InvalidRequestException("Source and destination stations must both be active");
        }

        List<TimetableSearchResult> results = trainStopRepository
                .searchTimetables(sourceCode, destinationCode).stream()
                .map(TimetableService::toResult)
                .toList();

        return new TimetableSearchResponse(sourceCode, destinationCode, results.size(), results);
    }

    private static TimetableSearchResult toResult(Object[] row) {
        Train train = (Train) row[0];
        Station sourceStation = (Station) row[1];
        Station destinationStation = (Station) row[2];
        LocalTime departureTime = (LocalTime) row[3];
        Integer departureDayOffset = (Integer) row[4];
        Integer sourceDistanceKm = (Integer) row[5];
        LocalTime arrivalTime = (LocalTime) row[6];
        Integer arrivalDayOffset = (Integer) row[7];
        Integer destinationDistanceKm = (Integer) row[8];

        Integer distanceKm = (sourceDistanceKm == null || destinationDistanceKm == null)
                ? null
                : destinationDistanceKm - sourceDistanceKm;

        return new TimetableSearchResult(
                train.getTrainNumber(),
                train.getName(),
                train.getTrainType(),
                StationSummaryResponse.from(sourceStation),
                StationSummaryResponse.from(destinationStation),
                departureTime,
                departureDayOffset,
                arrivalTime,
                arrivalDayOffset,
                distanceKm);
    }

    private Station findStation(String stationCode) {
        return stationRepository.findByStationCode(stationCode)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with code: " + stationCode));
    }
}
