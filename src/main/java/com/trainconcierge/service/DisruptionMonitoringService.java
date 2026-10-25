package com.trainconcierge.service;

import com.trainconcierge.config.TrainConciergeProperties;
import com.trainconcierge.entity.TrainStatus;
import com.trainconcierge.repository.TrainStatusRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * One disruption-monitoring cycle.
 *
 * <p>Reads the simulated running statuses inside the configured service-date
 * window and hands each one to {@link DisruptionEventService}. Each train/date is
 * applied in its own transaction, so one failure cannot roll back the others and
 * is reported as a failure count instead. Safe to call from the scheduler and
 * directly from tests or the manual API.</p>
 */
@Service
public class DisruptionMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(DisruptionMonitoringService.class);

    private final TrainStatusRepository trainStatusRepository;
    private final DisruptionEventService disruptionEventService;
    private final TrainConciergeProperties properties;

    public DisruptionMonitoringService(TrainStatusRepository trainStatusRepository,
                                       DisruptionEventService disruptionEventService,
                                       TrainConciergeProperties properties) {
        this.trainStatusRepository = trainStatusRepository;
        this.disruptionEventService = disruptionEventService;
        this.properties = properties;
    }

    /**
     * Runs a full monitoring cycle. Missing status data is simply absent from the
     * inspected set, so it can never be mistaken for an on-time train.
     */
    public MonitoringRunResult runMonitoring() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(properties.getDisruption().getLookbackDays());
        LocalDate to = today.plusDays(properties.getDisruption().getForwardDays());

        int created = 0;
        int updated = 0;
        int resolved = 0;
        int failures = 0;

        var statuses = trainStatusRepository.findStatusesInServiceDateRange(from, to);

        for (TrainStatus status : statuses) {
            Long trainId = status.getTrain().getId();
            LocalDate serviceDate = status.getServiceDate();
            int delayMinutes = status.getDelayMinutes();
            String reason = status.getReason();

            try {
                ObservationResult result = disruptionEventService.applyObservation(
                        trainId, serviceDate, status.getStatus(), delayMinutes, reason);
                created += result.created();
                updated += result.updated();
                resolved += result.resolved();
            } catch (DataIntegrityViolationException ex) {
                log.warn("Concurrent disruption event detected for train {} on {}; refreshing it instead",
                        trainId, serviceDate);
                try {
                    ObservationResult result = disruptionEventService.refreshConcurrentEvent(
                            trainId, serviceDate, status.getStatus(), delayMinutes, reason);
                    created += result.created();
                    updated += result.updated();
                    resolved += result.resolved();
                } catch (RuntimeException retryFailure) {
                    failures++;
                    log.error("Failed to refresh concurrent disruption event for train {} on {}: {}",
                            trainId, serviceDate, retryFailure.getMessage());
                }
            } catch (RuntimeException ex) {
                failures++;
                log.error("Failed to apply disruption observation for train {} on {}: {}",
                        trainId, serviceDate, ex.getMessage());
            }
        }

        MonitoringRunResult result = new MonitoringRunResult(statuses.size(), created, updated, resolved, failures);
        if (log.isInfoEnabled()) {
            log.info("Disruption monitoring finished: statusesInspected={}, created={}, updated={}, resolved={}, failures={}",
                    result.statusesInspected(), result.eventsCreated(), result.eventsUpdated(),
                    result.eventsResolved(), result.failures());
        }
        return result;
    }
}
