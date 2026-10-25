package com.trainconcierge.service;

import com.trainconcierge.dto.JourneyLegRequest;
import com.trainconcierge.dto.JourneyRequest;
import com.trainconcierge.dto.JourneyResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.JourneyLeg;
import com.trainconcierge.entity.JourneyStatus;
import com.trainconcierge.entity.Passenger;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainStop;
import com.trainconcierge.exception.InvalidRequestException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.JourneyLegRepository;
import com.trainconcierge.repository.JourneyRepository;
import com.trainconcierge.repository.PassengerRepository;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStopRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final JourneyLegRepository journeyLegRepository;
    private final PassengerRepository passengerRepository;
    private final TrainRepository trainRepository;
    private final StationRepository stationRepository;
    private final TrainStopRepository trainStopRepository;

    public JourneyService(JourneyRepository journeyRepository,
                          JourneyLegRepository journeyLegRepository,
                          PassengerRepository passengerRepository,
                          TrainRepository trainRepository,
                          StationRepository stationRepository,
                          TrainStopRepository trainStopRepository) {
        this.journeyRepository = journeyRepository;
        this.journeyLegRepository = journeyLegRepository;
        this.passengerRepository = passengerRepository;
        this.trainRepository = trainRepository;
        this.stationRepository = stationRepository;
        this.trainStopRepository = trainStopRepository;
    }

    @Transactional
    public JourneyResponse create(JourneyRequest request) {
        Passenger passenger = passengerRepository.findById(request.passengerId())
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with id: " + request.passengerId()));

        if (!passenger.isActive()) {
            throw new InvalidRequestException("Passenger is not active");
        }

        validateJourneyDate(request.journeyDate());

        Journey journey = new Journey();
        journey.setPassenger(passenger);
        journey.setJourneyDate(request.journeyDate());
        journey.setJourneyReference(generateReference());
        journey.setStatus(JourneyStatus.PLANNED);

        validateAndApplyLegs(journey, request.legs());

        return JourneyResponse.from(journeyRepository.save(journey));
    }

    @Transactional(readOnly = true)
    public JourneyResponse getById(Long id) {
        return JourneyResponse.from(findJourney(id));
    }

    @Transactional(readOnly = true)
    public JourneyResponse getByReference(String reference) {
        return journeyRepository.findByJourneyReference(reference)
                .map(JourneyResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Journey not found with reference: " + reference));
    }

    @Transactional(readOnly = true)
    public PageResponse<JourneyResponse> search(Long passengerId, String reference,
                                                 LocalDate journeyDate, JourneyStatus status,
                                                 Pageable pageable) {
        Page<Journey> page = journeyRepository.search(
                passengerId,
                reference == null || reference.isBlank() ? null : reference.trim(),
                journeyDate,
                status,
                pageable);

        return PageResponse.from(page, JourneyResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<JourneyResponse> listByPassenger(Long passengerId, Pageable pageable) {
        if (!passengerRepository.existsById(passengerId)) {
            throw new ResourceNotFoundException("Passenger not found with id: " + passengerId);
        }
        Page<Journey> page = journeyRepository.findByPassengerId(passengerId, pageable);
        return PageResponse.from(page, JourneyResponse::from);
    }

    @Transactional
    public JourneyResponse updateItinerary(Long id, JourneyRequest request) {
        Journey journey = findJourney(id);

        if (journey.getStatus() == JourneyStatus.CANCELLED || journey.getStatus() == JourneyStatus.COMPLETED) {
            throw new InvalidRequestException("Cannot update a journey in " + journey.getStatus() + " status");
        }

        validateJourneyDate(request.journeyDate());
        journey.setJourneyDate(request.journeyDate());

        // Replace all legs
        journey.getLegs().clear();
        validateAndApplyLegs(journey, request.legs());

        return JourneyResponse.from(journeyRepository.save(journey));
    }

    @Transactional
    public JourneyResponse cancel(Long id) {
        Journey journey = findJourney(id);
        if (journey.getStatus() == JourneyStatus.CANCELLED) {
            throw new InvalidRequestException("Journey is already cancelled");
        }
        journey.setStatus(JourneyStatus.CANCELLED);
        return JourneyResponse.from(journeyRepository.save(journey));
    }

    private void validateAndApplyLegs(Journey journey, List<JourneyLegRequest> legRequests) {
        if (legRequests == null || legRequests.isEmpty()) {
            throw new InvalidRequestException("A journey must have at least one leg");
        }

        // 1. Sort by sequence to check continuity
        List<JourneyLegRequest> sortedRequests = legRequests.stream()
                .sorted((a, b) -> a.legSequence().compareTo(b.legSequence()))
                .toList();

        Set<Integer> sequences = new HashSet<>();
        Long lastDestinationId = null;
        List<JourneyLeg> legs = new ArrayList<>();

        for (int i = 0; i < sortedRequests.size(); i++) {
            JourneyLegRequest req = sortedRequests.get(i);

            // Sequence checks
            if (req.legSequence() != i + 1) {
                throw new InvalidRequestException("Leg sequences must be contiguous starting from 1");
            }
            if (!sequences.add(req.legSequence())) {
                throw new InvalidRequestException("Duplicate leg sequence: " + req.legSequence());
            }

            // Connection checks
            if (lastDestinationId != null && !lastDestinationId.equals(req.sourceStationId())) {
                throw new InvalidRequestException("Leg " + req.legSequence() + " source must match previous leg destination");
            }

            // Business logic checks
            JourneyLeg leg = createLeg(journey, req);
            legs.add(leg);
            lastDestinationId = req.destinationStationId();
        }

        journey.setLegs(legs);
    }

    private JourneyLeg createLeg(Journey journey, JourneyLegRequest req) {
        if (req.sourceStationId().equals(req.destinationStationId())) {
            throw new InvalidRequestException("Source and destination stations must be different in leg " + req.legSequence());
        }

        Train train = trainRepository.findById(req.trainId())
                .orElseThrow(() -> new ResourceNotFoundException("Train not found with id: " + req.trainId()));
        if (!train.isActive()) {
            throw new InvalidRequestException("Train " + train.getTrainNumber() + " is not active");
        }

        Station source = stationRepository.findById(req.sourceStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Source station not found with id: " + req.sourceStationId()));
        if (!source.isActive()) {
            throw new InvalidRequestException("Source station " + source.getStationCode() + " is not active");
        }

        Station destination = stationRepository.findById(req.destinationStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination station not found with id: " + req.destinationStationId()));
        if (!destination.isActive()) {
            throw new InvalidRequestException("Destination station " + destination.getStationCode() + " is not active");
        }

        // Timetable validation
        TrainStop srcStop = trainStopRepository.findByTrainIdAndStationId(train.getId(), source.getId())
                .orElseThrow(() -> new InvalidRequestException("Station " + source.getStationCode() + " is not a stop for train " + train.getTrainNumber()));

        TrainStop dstStop = trainStopRepository.findByTrainIdAndStationId(train.getId(), destination.getId())
                .orElseThrow(() -> new InvalidRequestException("Station " + destination.getStationCode() + " is not a stop for train " + train.getTrainNumber()));

        if (srcStop.getStopSequence() >= dstStop.getStopSequence()) {
            throw new InvalidRequestException("Source station must appear before destination station in train " + train.getTrainNumber());
        }

        JourneyLeg leg = new JourneyLeg();
        leg.setJourney(journey);
        leg.setTrain(train);
        leg.setSourceStation(source);
        leg.setDestinationStation(destination);
        leg.setLegSequence(req.legSequence());
        leg.setDepartureTime(srcStop.getDepartureTime());
        leg.setArrivalTime(dstStop.getArrivalTime());

        return leg;
    }

    private void validateJourneyDate(LocalDate date) {
        if (date.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("Journey date cannot be in the past");
        }
    }

    private String generateReference() {
        return "TC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Journey findJourney(Long id) {
        return journeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journey not found with id: " + id));
    }
}
