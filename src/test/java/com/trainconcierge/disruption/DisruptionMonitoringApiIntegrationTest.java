package com.trainconcierge.disruption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trainconcierge.dto.TrainStatusRequest;
import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.EventState;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.repository.DisruptionEventRepository;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStatusRepository;
import com.trainconcierge.service.DisruptionMonitoringService;
import com.trainconcierge.service.MonitoringRunResult;
import com.trainconcierge.service.TrainStatusService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "trainconcierge.monitoring.enabled=false",
        "trainconcierge.disruption.delay-threshold-minutes=30"
})
@AutoConfigureMockMvc
@Transactional
class DisruptionMonitoringApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private TrainStatusRepository trainStatusRepository;

    @Autowired
    private DisruptionEventRepository disruptionEventRepository;

    @Autowired
    private TrainStatusService trainStatusService;

    @Autowired
    private DisruptionMonitoringService disruptionMonitoringService;

    private final LocalDate serviceDate = LocalDate.now().plusDays(2);

    // 1. Delay exactly at the threshold
    @Test
    @DisplayName("a delay at the threshold raises a SIGNIFICANT_DELAY event")
    void delayAtThresholdCreatesEvent() {
        long trainId = createTrain("700001");
        setStatus(trainId, TrainRunningStatus.DELAYED, 30, "At threshold");

        MonitoringRunResult result = disruptionMonitoringService.runMonitoring();

        assertThat(result.eventsCreated()).isGreaterThanOrEqualTo(1);
        DisruptionEvent event = onlyEvent(trainId);
        assertThat(event.getDisruptionType()).isEqualTo(DisruptionType.SIGNIFICANT_DELAY);
        assertThat(event.getObservedTrainStatus()).isEqualTo(TrainRunningStatus.DELAYED);
        assertThat(event.getDelayMinutes()).isEqualTo(30);
        assertThat(event.getEventState()).isEqualTo(EventState.ACTIVE);
        assertThat(event.getDetectedAt()).isNotNull();
        assertThat(event.getResolvedAt()).isNull();
    }

    // 2. Delay below the threshold
    @Test
    @DisplayName("a delay below the threshold creates no event")
    void delayBelowThresholdCreatesNoEvent() {
        long trainId = createTrain("700002");
        setStatus(trainId, TrainRunningStatus.DELAYED, 29, "Below threshold");

        disruptionMonitoringService.runMonitoring();

        assertThat(eventsFor(trainId)).isEmpty();
    }

    // 3. Cancellation detection
    @Test
    @DisplayName("a cancelled train raises a CANCELLATION event")
    void cancellationCreatesEvent() {
        long trainId = createTrain("700003");
        setStatus(trainId, TrainRunningStatus.CANCELLED, 0, "Cancelled");

        disruptionMonitoringService.runMonitoring();

        DisruptionEvent event = onlyEvent(trainId);
        assertThat(event.getDisruptionType()).isEqualTo(DisruptionType.CANCELLATION);
        assertThat(event.getObservedTrainStatus()).isEqualTo(TrainRunningStatus.CANCELLED);
        assertThat(event.getDelayMinutes()).isZero();
    }

    // 4. On-time status
    @Test
    @DisplayName("an on-time train creates no event")
    void onTimeCreatesNoEvent() {
        long trainId = createTrain("700004");
        setStatus(trainId, TrainRunningStatus.ON_TIME, 0, null);

        disruptionMonitoringService.runMonitoring();

        assertThat(eventsFor(trainId)).isEmpty();
    }

    // 5. Missing status data
    @Test
    @DisplayName("a train with no status row is never treated as on time or disrupted")
    void missingStatusDataCreatesNoEvent() {
        long trainId = createTrain("700005");

        disruptionMonitoringService.runMonitoring();

        assertThat(trainStatusRepository.findByTrainIdAndServiceDate(trainId, serviceDate)).isEmpty();
        assertThat(eventsFor(trainId)).isEmpty();
    }

    // 6. Duplicate prevention across repeated runs
    @Test
    @DisplayName("repeated monitoring runs do not create duplicate events")
    void repeatedRunsDoNotDuplicate() {
        long trainId = createTrain("700006");
        setStatus(trainId, TrainRunningStatus.DELAYED, 45, "Summer heat");

        disruptionMonitoringService.runMonitoring();
        MonitoringRunResult second = disruptionMonitoringService.runMonitoring();

        assertThat(second.eventsCreated()).isZero();
        List<DisruptionEvent> events = eventsFor(trainId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventState()).isEqualTo(EventState.ACTIVE);
    }

    // 7. Ongoing event observations are refreshed
    @Test
    @DisplayName("an ongoing event is refreshed without changing its detectedAt")
    void ongoingEventIsRefreshed() {
        long trainId = createTrain("700007");
        setStatus(trainId, TrainRunningStatus.DELAYED, 45, "First observation");
        disruptionMonitoringService.runMonitoring();

        DisruptionEvent first = onlyEvent(trainId);
        Long firstId = first.getId();
        var detectedAt = first.getDetectedAt();

        setStatus(trainId, TrainRunningStatus.DELAYED, 90, "Worse");
        MonitoringRunResult second = disruptionMonitoringService.runMonitoring();

        assertThat(second.eventsCreated()).isZero();
        assertThat(second.eventsUpdated()).isGreaterThanOrEqualTo(1);

        List<DisruptionEvent> events = eventsFor(trainId);
        assertThat(events).hasSize(1);
        DisruptionEvent refreshed = events.get(0);
        assertThat(refreshed.getId()).isEqualTo(firstId);
        assertThat(refreshed.getDetectedAt()).isEqualTo(detectedAt);
        assertThat(refreshed.getDelayMinutes()).isEqualTo(90);
        assertThat(refreshed.getReason()).isEqualTo("Worse");
        assertThat(refreshed.getLastObservedAt()).isAfterOrEqualTo(detectedAt);
    }

    // 8. Resolution after restoration
    @Test
    @DisplayName("an active delay event is resolved when the train returns to ON_TIME")
    void restorationResolvesEvent() {
        long trainId = createTrain("700008");
        setStatus(trainId, TrainRunningStatus.DELAYED, 60, "Delayed");
        disruptionMonitoringService.runMonitoring();

        setStatus(trainId, TrainRunningStatus.ON_TIME, 0, null);
        disruptionMonitoringService.runMonitoring();

        DisruptionEvent event = onlyEvent(trainId);
        assertThat(event.getEventState()).isEqualTo(EventState.RESOLVED);
        assertThat(event.getResolvedAt()).isNotNull();
        assertThat(event.getActiveKey()).isNull();
    }

    // 9. Delay -> cancellation
    @Test
    @DisplayName("delay to cancellation resolves the delay event and raises a cancellation event")
    void delayToCancellationTransition() {
        long trainId = createTrain("700009");
        setStatus(trainId, TrainRunningStatus.DELAYED, 75, "Delayed");
        disruptionMonitoringService.runMonitoring();

        setStatus(trainId, TrainRunningStatus.CANCELLED, 0, "Cancelled after delay");
        disruptionMonitoringService.runMonitoring();

        List<DisruptionEvent> events = eventsFor(trainId);
        assertThat(events).hasSize(2);
        DisruptionEvent delay = eventOfType(events, DisruptionType.SIGNIFICANT_DELAY);
        DisruptionEvent cancellation = eventOfType(events, DisruptionType.CANCELLATION);
        assertThat(delay.getEventState()).isEqualTo(EventState.RESOLVED);
        assertThat(cancellation.getEventState()).isEqualTo(EventState.ACTIVE);
    }

    // 10. Cancellation -> delay
    @Test
    @DisplayName("cancellation to delay resolves the cancellation event and raises a delay event")
    void cancellationToDelayTransition() {
        long trainId = createTrain("700010");
        setStatus(trainId, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        disruptionMonitoringService.runMonitoring();

        setStatus(trainId, TrainRunningStatus.DELAYED, 45, "Running again but late");
        disruptionMonitoringService.runMonitoring();

        List<DisruptionEvent> events = eventsFor(trainId);
        assertThat(events).hasSize(2);
        assertThat(eventOfType(events, DisruptionType.CANCELLATION).getEventState()).isEqualTo(EventState.RESOLVED);
        assertThat(eventOfType(events, DisruptionType.SIGNIFICANT_DELAY).getEventState()).isEqualTo(EventState.ACTIVE);
    }

    // 11. Recurrence after resolution
    @Test
    @DisplayName("a disruption that recurs after resolution creates a new event")
    void recurrenceAfterResolutionCreatesNewEvent() {
        long trainId = createTrain("700011");
        setStatus(trainId, TrainRunningStatus.DELAYED, 45, "First");
        disruptionMonitoringService.runMonitoring();
        Long firstId = onlyEvent(trainId).getId();

        setStatus(trainId, TrainRunningStatus.ON_TIME, 0, null);
        disruptionMonitoringService.runMonitoring();

        setStatus(trainId, TrainRunningStatus.DELAYED, 60, "Recurrence");
        disruptionMonitoringService.runMonitoring();

        List<DisruptionEvent> events = eventsFor(trainId);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(DisruptionEvent::getId).doesNotContainNull().doesNotHaveDuplicates();
        DisruptionEvent open = events.stream().filter(e -> e.getEventState() != EventState.RESOLVED).findFirst()
                .orElseThrow();
        assertThat(open.getId()).isNotEqualTo(firstId);
        assertThat(open.getDelayMinutes()).isEqualTo(60);
    }

    // 12. Filtering and pagination
    @Test
    @DisplayName("GET /api/v1/disruptions filters and paginates deterministically")
    void filteringAndPagination() throws Exception {
        long delayedTrain = createTrain("700012");
        setStatus(delayedTrain, TrainRunningStatus.DELAYED, 120, "Big delay");
        long cancelledTrain = createTrain("700013");
        setStatus(cancelledTrain, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        disruptionMonitoringService.runMonitoring();

        mockMvc.perform(get("/api/v1/disruptions")
                        .param("trainId", String.valueOf(delayedTrain))
                        .param("disruptionType", "SIGNIFICANT_DELAY")
                        .param("minDelayMinutes", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trainId").value(delayedTrain))
                .andExpect(jsonPath("$.content[0].delayMinutes").value(120))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/disruptions")
                        .param("eventState", "ACTIVE")
                        .param("disruptionType", "CANCELLATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].disruptionType").value("CANCELLATION"));

        mockMvc.perform(get("/api/v1/trains/{trainId}/disruptions", cancelledTrain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/v1/disruptions").param("minDelayMinutes", "-1"))
                .andExpect(status().isBadRequest());
    }

    // 13. Missing id
    @Test
    @DisplayName("GET /api/v1/disruptions/{id} returns 404 when the event is absent")
    void missingEventReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/disruptions/{id}", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    // 14. Acknowledgement
    @Test
    @DisplayName("acknowledging an active event does not resolve it")
    void acknowledgeActiveEvent() throws Exception {
        long trainId = createTrain("700014");
        setStatus(trainId, TrainRunningStatus.DELAYED, 90, "Delayed");
        disruptionMonitoringService.runMonitoring();
        Long eventId = onlyEvent(trainId).getId();

        mockMvc.perform(post("/api/v1/disruptions/{id}/acknowledge", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventState").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty());

        // Acknowledged events are still ongoing: another run must not duplicate them.
        MonitoringRunResult rerun = disruptionMonitoringService.runMonitoring();
        assertThat(rerun.eventsCreated()).isZero();
        assertThat(eventsFor(trainId)).hasSize(1);

        // Resolve it, then acknowledgement must be rejected.
        setStatus(trainId, TrainRunningStatus.ON_TIME, 0, null);
        disruptionMonitoringService.runMonitoring();
        mockMvc.perform(post("/api/v1/disruptions/{id}/acknowledge", eventId))
                .andExpect(status().isBadRequest());
    }

    // 15. Database-level duplicate prevention
    @Test
    @DisplayName("the unique active_key constraint rejects a duplicate ongoing event")
    void uniqueActiveKeyRejectsDuplicateOngoingEvent() {
        long trainId = createTrain("700015");
        String key = "T%d:%s:%s".formatted(trainId, serviceDate, DisruptionType.SIGNIFICANT_DELAY);

        disruptionEventRepository.saveAndFlush(ongoingEvent(trainId, key));

        assertThatThrownBy(() -> disruptionEventRepository.saveAndFlush(ongoingEvent(trainId, key)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 16. Manual monitoring endpoint
    @Test
    @DisplayName("POST /api/v1/monitoring/run is idempotent on repeated calls")
    void monitoringRunEndpointIsIdempotent() throws Exception {
        long trainId = createTrain("700016");
        setStatus(trainId, TrainRunningStatus.DELAYED, 50, "Delayed");

        mockMvc.perform(post("/api/v1/monitoring/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusesInspected").isNumber())
                .andExpect(jsonPath("$.eventsCreated").isNumber());

        mockMvc.perform(post("/api/v1/monitoring/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsCreated").value(0));

        assertThat(eventsFor(trainId)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setStatus(long trainId, TrainRunningStatus status, Integer delayMinutes, String reason) {
        trainStatusService.upsert(trainId,
                new TrainStatusRequest(serviceDate, status, delayMinutes, null, reason, null, null));
    }

    private List<DisruptionEvent> eventsFor(long trainId) {
        return disruptionEventRepository
                .search(trainId, serviceDate, null, null, null, PageRequest.of(0, 50))
                .getContent();
    }

    private DisruptionEvent onlyEvent(long trainId) {
        List<DisruptionEvent> events = eventsFor(trainId);
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private DisruptionEvent eventOfType(List<DisruptionEvent> events, DisruptionType type) {
        return events.stream().filter(e -> e.getDisruptionType() == type).findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " event found"));
    }

    private DisruptionEvent ongoingEvent(long trainId, String activeKey) {
        DisruptionEvent event = new DisruptionEvent();
        event.setTrain(trainRepository.getReferenceById(trainId));
        event.setServiceDate(serviceDate);
        event.setDisruptionType(DisruptionType.SIGNIFICANT_DELAY);
        event.setObservedTrainStatus(TrainRunningStatus.DELAYED);
        event.setDelayMinutes(45);
        event.setDetectedAt(java.time.OffsetDateTime.now());
        event.setLastObservedAt(java.time.OffsetDateTime.now());
        event.setEventState(EventState.ACTIVE);
        event.setActiveKey(activeKey);
        return event;
    }

    private long createTrain(String trainNumber) {
        Station source = newStation("A" + trainNumber, "Source " + trainNumber);
        Station destination = newStation("B" + trainNumber, "Destination " + trainNumber);

        Train train = new Train();
        train.setTrainNumber(trainNumber);
        train.setName("Disruption Demo " + trainNumber);
        train.setSourceStation(source);
        train.setDestinationStation(destination);
        train.setActive(true);
        return trainRepository.save(train).getId();
    }

    private Station newStation(String code, String name) {
        Station station = new Station();
        station.setStationCode(code);
        station.setName(name);
        station.setCity("Demo");
        station.setActive(true);
        return stationRepository.save(station);
    }
}
