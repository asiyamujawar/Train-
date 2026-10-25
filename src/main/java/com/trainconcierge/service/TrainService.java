package com.trainconcierge.service;

import static com.trainconcierge.service.StationService.blankToNull;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.TrainRequest;
import com.trainconcierge.dto.TrainResponse;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainType;
import com.trainconcierge.exception.ConflictException;
import com.trainconcierge.exception.InvalidRequestException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Train master-data operations. */
@Service
public class TrainService {

    private final TrainRepository trainRepository;
    private final StationRepository stationRepository;

    public TrainService(TrainRepository trainRepository, StationRepository stationRepository) {
        this.trainRepository = trainRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional
    public TrainResponse create(TrainRequest request) {
        String trainNumber = request.trainNumber().trim();
        if (trainRepository.existsByTrainNumber(trainNumber)) {
            throw new ConflictException("Train number '" + trainNumber + "' already exists");
        }

        Station sourceStation = findStation(request.sourceStationId());
        Station destinationStation = findStation(request.destinationStationId());
        requireDistinctStations(sourceStation, destinationStation);

        Train train = new Train();
        train.setTrainNumber(trainNumber);
        train.setName(request.name().trim());
        train.setTrainType(request.trainType());
        train.setSourceStation(sourceStation);
        train.setDestinationStation(destinationStation);
        train.setActive(request.active() == null || request.active());

        return TrainResponse.from(trainRepository.save(train));
    }

    @Transactional(readOnly = true)
    public TrainResponse getById(Long id) {
        return TrainResponse.from(findTrainDetail(id));
    }

    @Transactional(readOnly = true)
    public TrainResponse getByTrainNumber(String trainNumber) {
        String number = trainNumber.trim();
        Train train = trainRepository.findDetailByTrainNumber(number)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found with number: " + number));

        return TrainResponse.from(train);
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainResponse> search(String trainNumber, String name, TrainType trainType,
                                              Long sourceStationId, Long destinationStationId,
                                              Boolean active, Pageable pageable) {
        Page<Train> page = trainRepository.search(
                blankToNull(trainNumber),
                blankToNull(name),
                trainType,
                sourceStationId,
                destinationStationId,
                active,
                pageable);

        return PageResponse.from(page, TrainResponse::from);
    }

    @Transactional
    public TrainResponse update(Long id, TrainRequest request) {
        Train train = findTrainDetail(id);
        String trainNumber = request.trainNumber().trim();
        if (trainRepository.existsByTrainNumberAndIdNot(trainNumber, id)) {
            throw new ConflictException("Train number '" + trainNumber + "' already exists");
        }

        Station sourceStation = findStation(request.sourceStationId());
        Station destinationStation = findStation(request.destinationStationId());
        requireDistinctStations(sourceStation, destinationStation);

        train.setTrainNumber(trainNumber);
        train.setName(request.name().trim());
        train.setTrainType(request.trainType());
        train.setSourceStation(sourceStation);
        train.setDestinationStation(destinationStation);
        if (request.active() != null) {
            train.setActive(request.active());
        }

        return TrainResponse.from(trainRepository.save(train));
    }

    /** Soft delete: timetables and history stay intact. */
    @Transactional
    public TrainResponse deactivate(Long id) {
        Train train = findTrainDetail(id);
        train.setActive(false);

        return TrainResponse.from(trainRepository.save(train));
    }

    private void requireDistinctStations(Station sourceStation, Station destinationStation) {
        if (sourceStation.getId().equals(destinationStation.getId())) {
            throw new InvalidRequestException("Source and destination stations must be different");
        }
    }

    private Station findStation(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + id));
    }

    private Train findTrainDetail(Long id) {
        return trainRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found with id: " + id));
    }
}
