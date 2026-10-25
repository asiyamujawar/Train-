package com.trainconcierge.trainstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.dto.TrainStatusRequest;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStatusHistoryRepository;
import com.trainconcierge.repository.TrainStatusRepository;
import com.trainconcierge.service.TrainStatusService;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainStatusApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainStatusRepository trainStatusRepository;

    @Autowired
    private TrainStatusHistoryRepository trainStatusHistoryRepository;

    @Autowired
    private TrainStatusService trainStatusService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final LocalDate serviceDate = LocalDate.now().plusDays(5);

    // 1. Initial ON_TIME creation
    @Test
    @DisplayName("PUT creates an initial ON_TIME status with 201")
    void createInitialOnTimeStatus() throws Exception {
        long trainId = createTrain("800001");

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"ON_TIME","delayMinutes":0}
                                """.formatted(serviceDate)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("ON_TIME"))
                .andExpect(jsonPath("$.delayMinutes").value(0))
                .andExpect(jsonPath("$.trainId").value(trainId));

        assertThat(trainStatusHistoryRepository
                .findByTrainIdAndServiceDateOrderByChangedAtAscIdAsc(trainId, serviceDate))
                .hasSize(1)
                .first()
                .satisfies(h -> {
                    assertThat(h.getPreviousStatus()).isNull();
                    assertThat(h.getNewStatus()).isEqualTo(TrainRunningStatus.ON_TIME);
                });
    }

    // 2. DELAYED with positive delay
    @Test
    @DisplayName("PUT creates a DELAYED status with a positive delay")
    void createDelayedStatus() throws Exception {
        long trainId = createTrain("800002");

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"DELAYED","delayMinutes":45,"reason":"Signalling"}
                                """.formatted(serviceDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DELAYED"))
                .andExpect(jsonPath("$.delayMinutes").value(45))
                .andExpect(jsonPath("$.reason").value("Signalling"));
    }

    // 3. CANCELLED (delay stored as 0)
    @Test
    @DisplayName("PUT creates a CANCELLED status with a stored delay of 0")
    void createCancelledStatus() throws Exception {
        long trainId = createTrain("800003");

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"CANCELLED","reason":"Operational disruption"}
                                """.formatted(serviceDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.delayMinutes").value(0));
    }

    // 4. Update a delayed train to a larger delay
    @Test
    @DisplayName("a delayed train can be updated to a larger delay")
    void updateDelayedToLargerDelay() throws Exception {
        long trainId = createTrain("800004");
        putStatus(trainId, """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":30}
                """.formatted(serviceDate), 201);

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"DELAYED","delayMinutes":90}
                                """.formatted(serviceDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayMinutes").value(90));

        var history = trainStatusHistoryRepository
                .findByTrainIdAndServiceDateOrderByChangedAtAscIdAsc(trainId, serviceDate);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getPreviousStatus()).isEqualTo(TrainRunningStatus.DELAYED);
        assertThat(history.get(1).getPreviousDelayMinutes()).isEqualTo(30);
        assertThat(history.get(1).getNewDelayMinutes()).isEqualTo(90);
    }

    // 5. Restore a cancelled train to ON_TIME
    @Test
    @DisplayName("a cancelled train can be restored to ON_TIME and keeps its history")
    void restoreCancelledTrain() throws Exception {
        long trainId = createTrain("800005");
        putStatus(trainId, """
                {"serviceDate":"%s","status":"CANCELLED"}
                """.formatted(serviceDate), 201);

        mockMvc.perform(post("/api/v1/trains/{id}/status/restore", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","reason":"Restored"}
                                """.formatted(serviceDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_TIME"))
                .andExpect(jsonPath("$.delayMinutes").value(0));

        var history = trainStatusHistoryRepository
                .findByTrainIdAndServiceDateOrderByChangedAtAscIdAsc(trainId, serviceDate);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getNewStatus()).isEqualTo(TrainRunningStatus.CANCELLED);
        assertThat(history.get(1).getPreviousStatus()).isEqualTo(TrainRunningStatus.CANCELLED);
        assertThat(history.get(1).getNewStatus()).isEqualTo(TrainRunningStatus.ON_TIME);
    }

    // 6. Read current status
    @Test
    @DisplayName("GET returns the current status for a train and service date")
    void getCurrentStatus() throws Exception {
        long trainId = createTrain("800006");
        putStatus(trainId, """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":25}
                """.formatted(serviceDate), 201);

        mockMvc.perform(get("/api/v1/trains/{id}/status", trainId)
                        .param("serviceDate", serviceDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"))
                .andExpect(jsonPath("$.delayMinutes").value(25))
                .andExpect(jsonPath("$.serviceDate").value(serviceDate.toString()));
    }

    // 7. Not yet reported -> 404 (never silently ON_TIME)
    @Test
    @DisplayName("GET returns 404 when no status has been reported")
    void statusNotReported() throws Exception {
        long trainId = createTrain("800007");

        mockMvc.perform(get("/api/v1/trains/{id}/status", trainId)
                        .param("serviceDate", serviceDate.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "No running status reported for train %d on %s".formatted(trainId, serviceDate)));
    }

    // 8. History is ascending
    @Test
    @DisplayName("status history is returned oldest first")
    void historyAscending() throws Exception {
        long trainId = createTrain("800008");
        putStatus(trainId, """
                {"serviceDate":"%s","status":"ON_TIME","delayMinutes":0}
                """.formatted(serviceDate), 201);
        putStatus(trainId, """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":60}
                """.formatted(serviceDate), 200);
        putStatus(trainId, """
                {"serviceDate":"%s","status":"CANCELLED"}
                """.formatted(serviceDate), 200);

        mockMvc.perform(get("/api/v1/trains/{id}/status-history", trainId)
                        .param("serviceDate", serviceDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].newStatus").value("ON_TIME"))
                .andExpect(jsonPath("$[1].newStatus").value("DELAYED"))
                .andExpect(jsonPath("$[2].newStatus").value("CANCELLED"));
    }

    // 9. Negative delay rejected
    @Test
    @DisplayName("a negative delay is rejected with 400")
    void rejectNegativeDelay() throws Exception {
        long trainId = createTrain("800009");

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"DELAYED","delayMinutes":-5}
                                """.formatted(serviceDate)))
                .andExpect(status().isBadRequest());
    }

    // 10. DELAYED with zero delay rejected
    @Test
    @DisplayName("a DELAYED status with zero delay is rejected with 400")
    void rejectDelayedWithZeroDelay() throws Exception {
        long trainId = createTrain("800010");

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"DELAYED","delayMinutes":0}
                                """.formatted(serviceDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A DELAYED status must have a positive delay"));
    }

    // 11. ON_TIME with positive delay rejected
    @Test
    @DisplayName("an ON_TIME status with a positive delay is rejected with 400")
    void rejectOnTimeWithPositiveDelay() throws Exception {
        long trainId = createTrain("800011");

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"ON_TIME","delayMinutes":10}
                                """.formatted(serviceDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An ON_TIME status must have a zero delay"));
    }

    // 12. Unknown train / invalid station
    @Test
    @DisplayName("an unknown train returns 404")
    void rejectUnknownTrain() throws Exception {
        mockMvc.perform(put("/api/v1/trains/{id}/status", 999_999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"ON_TIME","delayMinutes":0}
                                """.formatted(serviceDate)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an invalid current station id returns 404")
    void rejectInvalidStation() throws Exception {
        long trainId = createTrain("800012");

        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceDate":"%s","status":"DELAYED","delayMinutes":10,"currentStationId":999999999}
                                """.formatted(serviceDate)))
                .andExpect(status().isNotFound());
    }

    // 13. No duplicate current-status rows
    @Test
    @DisplayName("repeated updates keep a single current status row per train and date")
    void preventDuplicateCurrentStatusRows() throws Exception {
        long trainId = createTrain("800013");
        putStatus(trainId, """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":10}
                """.formatted(serviceDate), 201);
        putStatus(trainId, """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":20}
                """.formatted(serviceDate), 200);

        assertThat(trainStatusRepository.findByTrainIdAndServiceDate(trainId, serviceDate)).isPresent();
        assertThat(trainStatusRepository.search(trainId, serviceDate, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);
    }

    // 14. No duplicate history for an unchanged status
    @Test
    @DisplayName("repeating an identical status does not append a duplicate history row")
    void avoidDuplicateHistoryForUnchangedStatus() throws Exception {
        long trainId = createTrain("800014");
        String body = """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":60,"reason":"Crew change"}
                """.formatted(serviceDate);
        putStatus(trainId, body, 201);
        putStatus(trainId, body, 200);

        assertThat(trainStatusHistoryRepository
                .findByTrainIdAndServiceDateOrderByChangedAtAscIdAsc(trainId, serviceDate)).hasSize(1);
    }

    // 15. Atomic rollback of current status + history
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("current status and history are rolled back together when persistence fails")
    void rollbackStatusAndHistoryTogether() {
        long trainId = createTrain("800015");
        LocalDate date = LocalDate.now().plusDays(9);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> {
            trainStatusService.upsert(trainId,
                    new TrainStatusRequest(date, TrainRunningStatus.DELAYED, 30, null, "rollback test", null, null));
            throw new IllegalStateException("simulated failure after persist");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(trainStatusRepository.findByTrainIdAndServiceDate(trainId, date)).isEmpty();
        assertThat(trainStatusHistoryRepository
                .findByTrainIdAndServiceDateOrderByChangedAtAscIdAsc(trainId, date)).isEmpty();

        // This test runs without the class-level transaction, so clean up manually.
        trainRepository.deleteById(trainId);
        stationRepository.deleteAll(stationRepository.findAll().stream()
                .filter(s -> s.getStationCode().endsWith("800015"))
                .toList());
    }

    // Extra: filtered paginated list + simulation convenience endpoint
    @Test
    @DisplayName("GET /api/v1/train-statuses filters by status and delay threshold")
    void listStatusesWithFilters() throws Exception {
        long slowTrain = createTrain("800016");
        long fastTrain = createTrain("800017");
        putStatus(slowTrain, """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":120}
                """.formatted(serviceDate), 201);
        putStatus(fastTrain, """
                {"serviceDate":"%s","status":"DELAYED","delayMinutes":15}
                """.formatted(serviceDate), 201);

        mockMvc.perform(get("/api/v1/train-statuses")
                        .param("status", "DELAYED")
                        .param("minDelayMinutes", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trainId").value(slowTrain))
                .andExpect(jsonPath("$.content[0].delayMinutes").value(120));
    }

    @Test
    @DisplayName("POST /api/v1/simulations/train-status creates a status")
    void simulationEndpointCreatesStatus() throws Exception {
        long trainId = createTrain("800018");

        mockMvc.perform(post("/api/v1/simulations/train-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainId":%d,"serviceDate":"%s","status":"DELAYED","delayMinutes":75,"reason":"Demo"}
                                """.formatted(trainId, serviceDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DELAYED"))
                .andExpect(jsonPath("$.delayMinutes").value(75));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void putStatus(long trainId, String body, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/v1/trains/{id}/status", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    private long createTrain(String trainNumber) {
        Station source = newStation("A" + trainNumber, "Source " + trainNumber);
        Station destination = newStation("B" + trainNumber, "Destination " + trainNumber);

        Train train = new Train();
        train.setTrainNumber(trainNumber);
        train.setName("Status Demo " + trainNumber);
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
