package com.trainconcierge.service;

import com.trainconcierge.dto.TrainStopRequest;
import com.trainconcierge.dto.TrainStopResponse;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainStop;
import com.trainconcierge.exception.ConflictException;
import com.trainconcierge.exception.InvalidRequestException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStopRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the ordered stops (timetable entries) of a train.
 *
 * <p>Rules enforced here: a train must exist, the station must exist and be
 * active, a stop must define at least one scheduled time, and neither the stop
 * sequence nor the station may be repeated for the same train.</p>
 */
@Service
public class TrainStopService {

    private final TrainStopRepository trainStopRepository;
    private final TrainRepository trainRepository;
    private final StationRepository stationRepository;

    public TrainStopService(TrainStopRepository trainStopRepository,
                            TrainRepository trainRepository,
                            StationRepository stationRepository) {
        this.trainStopRepository = trainStopRepository;
        this.trainRepository = trainRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional
    public TrainStopResponse addStop(Long trainId, TrainStopRequest request) {
        Train train = findTrain(trainId);
        Station station = findStation(request.stationId());
        validateSchedule(request);

        if (trainStopRepository.existsByTrainIdAndStopSequence(trainId, request.stopSequence())) {
            throw new ConflictException("Stop sequence " + request.stopSequence()
                    + " already exists for train " + train.getTrainNumber());
        }
        if (trainStopRepository.existsByTrainIdAndStationId(trainId, station.getId())) {
            throw new ConflictException("Station " + station.getStationCode()
                    + " is already a stop of train " + train.getTrainNumber());
        }

        TrainStop stop = new TrainStop();
        stop.setTrain(train);
        stop.setStation(station);
        apply(stop, request);

        return TrainStopResponse.from(trainStopRepository.save(stop), trainId);
    }

    @Transactional(readOnly = true)
    public List<TrainStopResponse> listStops(Long trainId) {
        findTrain(trainId);

        return trainStopRepository.findStopsWithStation(trainId).stream()
                .map(stop -> TrainStopResponse.from(stop, trainId))
                .toList();
    }

    @Transactional
    public TrainStopResponse updateStop(Long trainId, Long stopId, TrainStopRequest request) {
        Train train = findTrain(trainId);
        TrainStop stop = findStop(trainId, stopId);
        Station station = findStation(request.stationId());
        validateSchedule(request);

        if (!stop.getStopSequence().equals(request.stopSequence())
                && trainStopRepository.existsByTrainIdAndStopSequenceAndIdNot(
                        trainId, request.stopSequence(), stopId)) {
            throw new ConflictException("Stop sequence " + request.stopSequence()
                    + " already exists for train " + train.getTrainNumber());
        }
        if (!stop.getStation().getId().equals(station.getId())
                && trainStopRepository.existsByTrainIdAndStationIdAndIdNot(trainId, station.getId(), stopId)) {
            throw new ConflictException("Station " + station.getStationCode()
                    + " is already a stop of train " + train.getTrainNumber());
        }

        stop.setStation(station);
        apply(stop, request);

        return TrainStopResponse.from(trainStopRepository.save(stop), trainId);
    }

    @Transactional
    public void deleteStop(Long trainId, Long stopId) {
        findTrain(trainId);
        trainStopRepository.delete(findStop(trainId, stopId));
    }

    private void validateSchedule(TrainStopRequest request) {
        if (request.arrivalTime() == null && request.departureTime() == null) {
            throw new InvalidRequestException(
                    "A stop must define at least one of arrivalTime or departureTime");
        }
    }

    private void apply(TrainStop stop, TrainStopRequest request) {
        stop.setStopSequence(request.stopSequence());
        stop.setArrivalTime(request.arrivalTime());
        stop.setDepartureTime(request.departureTime());
        stop.setDayOffset(request.dayOffset() == null ? 0 : request.dayOffset());
        stop.setDistanceFromSourceKm(request.distanceFromSourceKm());
    }

    private Train findTrain(Long trainId) {
        return trainRepository.findById(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found with id: " + trainId));
    }

    private Station findStation(Long stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + stationId));
        if (!station.isActive()) {
            throw new InvalidRequestException("Station " + station.getStationCode() + " is not active");
        }
        return station;
    }

    private TrainStop findStop(Long trainId, Long stopId) {
        return trainStopRepository.findByIdAndTrainId(stopId, trainId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stop " + stopId + " not found for train " + trainId));
    }
}
