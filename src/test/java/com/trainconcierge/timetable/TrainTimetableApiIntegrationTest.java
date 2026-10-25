package com.trainconcierge.timetable;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.entity.Station;
import com.trainconcierge.repository.StationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Train, train-stop and timetable tests against the real application, the real
 * MySQL schema and the V3 seed data. The test transaction is rolled back.
 * MySQL must be running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainTimetableApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationRepository stationRepository;

    // ------------------------------------------------------------------
    // Train master data
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/trains creates a train and returns its stations with 201")
    void createTrainReturnsCreatedWithStations() throws Exception {
        long source = createStation("ZTA1", "Alpha Junction", "Alphaville");
        long destination = createStation("ZTB1", "Beta Junction", "Betaville");

        mockMvc.perform(post("/api/v1/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainBody("90001", "Zeta Express", "EXPRESS", source, destination)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trainNumber").value("90001"))
                .andExpect(jsonPath("$.name").value("Zeta Express"))
                .andExpect(jsonPath("$.trainType").value("EXPRESS"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sourceStation.id").value((int) source))
                .andExpect(jsonPath("$.sourceStation.stationCode").value("ZTA1"))
                .andExpect(jsonPath("$.destinationStation.stationCode").value("ZTB1"));
    }

    @Test
    @DisplayName("a train whose source and destination are the same station is rejected with 400")
    void trainWithSameSourceAndDestinationReturnsBadRequest() throws Exception {
        long station = createStation("ZTC1", "Gamma Junction", "Gammaville");

        mockMvc.perform(post("/api/v1/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainBody("90002", "Loop Express", "PASSENGER", station, station)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Source and destination stations must be different"));
    }

    @Test
    @DisplayName("a train referencing an unknown station is rejected with 404")
    void trainWithUnknownStationReturnsNotFound() throws Exception {
        long station = createStation("ZTD1", "Delta Junction", "Deltaville");

        mockMvc.perform(post("/api/v1/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainBody("90003", "Ghost Express", "EXPRESS", station, 999_999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Station not found with id: 999999999"));
    }

    @Test
    @DisplayName("a duplicate train number is rejected with 409")
    void duplicateTrainNumberReturnsConflict() throws Exception {
        long source = createStation("ZTE1", "Epsilon Junction", "Epsilonville");
        long destination = createStation("ZTF1", "Zeta Town", "Zetatown");
        String body = trainBody("90004", "Duplicate Express", "SUPERFAST", source, destination);

        mockMvc.perform(post("/api/v1/trains").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trains").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Train number '90004' already exists"));
    }

    @Test
    @DisplayName("GET /api/v1/trains filters by train number with pagination")
    void trainSearchFiltersByTrainNumber() throws Exception {
        long source = createStation("ZTG1", "Eta Junction", "Etaville");
        long destination = createStation("ZTH1", "Theta Town", "Thetatown");

        mockMvc.perform(post("/api/v1/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainBody("90005", "Search Express", "EXPRESS", source, destination)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/trains").param("trainNumber", "90005").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trainNumber").value("90005"));
    }

    // ------------------------------------------------------------------
    // Train stops and timetable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("stops are returned in stop-sequence order and the timetable lists them in order")
    void stopsAndTimetableAreOrdered() throws Exception {
        long source = createStation("ZTJ1", "Iota Junction", "Iotaville");
        long destination = createStation("ZTK1", "Kappa Junction", "Kappaville");
        long trainId = createTrain("90010", "Order Express", "EXPRESS", source, destination);

        addStop(trainId, source, 1, null, "06:00:00", 0, 0);
        addStop(trainId, destination, 2, "09:00:00", null, 0, 240);

        mockMvc.perform(get("/api/v1/trains/{trainId}/stops", trainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].stopSequence").value(1))
                .andExpect(jsonPath("$[0].station.stationCode").value("ZTJ1"))
                .andExpect(jsonPath("$[0].departureTime").value("06:00:00"))
                .andExpect(jsonPath("$[1].stopSequence").value(2))
                .andExpect(jsonPath("$[1].station.stationCode").value("ZTK1"))
                .andExpect(jsonPath("$[1].arrivalTime").value("09:00:00"));

        mockMvc.perform(get("/api/v1/trains/{trainId}/timetable", trainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.train.trainNumber").value("90010"))
                .andExpect(jsonPath("$.train.destinationStation.stationCode").value("ZTK1"))
                .andExpect(jsonPath("$.stops", hasSize(2)))
                .andExpect(jsonPath("$.stops[0].stopSequence").value(1))
                .andExpect(jsonPath("$.stops[1].stopSequence").value(2));
    }

    @Test
    @DisplayName("a duplicate stop sequence for the same train is rejected with 409")
    void duplicateStopSequenceReturnsConflict() throws Exception {
        long source = createStation("ZTL1", "Lambda Junction", "Lambdaville");
        long other = createStation("ZTM1", "Mu Junction", "Muville");
        long trainId = createTrain("90011", "Sequence Express", "EXPRESS", source, other);

        addStop(trainId, source, 1, null, "06:00:00", 0, 0);

        mockMvc.perform(post("/api/v1/trains/{trainId}/stops", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stopBody(other, 1, "07:00:00", null, 0, 50)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Stop sequence 1 already exists for train 90011"));
    }

    @Test
    @DisplayName("the same station listed twice for one train is rejected with 409")
    void duplicateStationStopReturnsConflict() throws Exception {
        long source = createStation("ZTN1", "Nu Junction", "Nuville");
        long other = createStation("ZTO1", "Xi Junction", "Xiville");
        long trainId = createTrain("90012", "Repeat Express", "EXPRESS", source, other);

        addStop(trainId, source, 1, null, "06:00:00", 0, 0);

        mockMvc.perform(post("/api/v1/trains/{trainId}/stops", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stopBody(source, 2, "07:00:00", null, 0, 50)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Station ZTN1 is already a stop of train 90012"));
    }

    @Test
    @DisplayName("a stop without any scheduled time is rejected with 400")
    void stopWithoutAnyTimeReturnsBadRequest() throws Exception {
        long source = createStation("ZTP1", "Omicron Junction", "Omicronville");
        long other = createStation("ZTQ1", "Pi Junction", "Piville");
        long trainId = createTrain("90013", "Timeless Express", "EXPRESS", source, other);

        mockMvc.perform(post("/api/v1/trains/{trainId}/stops", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationId":%d,"stopSequence":1,"dayOffset":0}
                                """.formatted(source)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A stop must define at least one of arrivalTime or departureTime"));
    }

    @Test
    @DisplayName("a stop can be updated and deleted")
    void stopCanBeUpdatedAndDeleted() throws Exception {
        long source = createStation("ZTR1", "Rho Junction", "Rhoville");
        long other = createStation("ZTS1", "Sigma Junction", "Sigmaville");
        long trainId = createTrain("90014", "Mutable Express", "EXPRESS", source, other);
        long stopId = addStop(trainId, source, 1, null, "06:00:00", 0, 0);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/trains/{trainId}/stops/{stopId}", trainId, stopId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stopBody(source, 1, null, "06:30:00", 0, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureTime").value("06:30:00"));

        mockMvc.perform(delete("/api/v1/trains/{trainId}/stops/{stopId}", trainId, stopId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/trains/{trainId}/stops", trainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("a stop for an unknown train returns 404")
    void stopsForUnknownTrainReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/trains/{trainId}/stops", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Train not found with id: 999999999"));
    }

    // ------------------------------------------------------------------
    // Timetable search (uses the deterministic V3 seed data)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("timetable search returns the seeded train between PUNE and SUR")
    void timetableSearchReturnsMatchingTrain() throws Exception {
        mockMvc.perform(get("/api/v1/timetables/search")
                        .param("sourceStationCode", "pune")
                        .param("destinationStationCode", "SUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceStationCode").value("PUNE"))
                .andExpect(jsonPath("$.destinationStationCode").value("SUR"))
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].trainNumber").value("11013"))
                .andExpect(jsonPath("$.results[0].sourceStation.stationCode").value("PUNE"))
                .andExpect(jsonPath("$.results[0].destinationStation.stationCode").value("SUR"))
                .andExpect(jsonPath("$.results[0].departureTime").value("08:00:00"))
                .andExpect(jsonPath("$.results[0].arrivalTime").value("12:30:00"))
                .andExpect(jsonPath("$.results[0].distanceKm").value(264));
    }

    @Test
    @DisplayName("timetable search excludes a train that visits the destination before the source")
    void timetableSearchExcludesReversedOrder() throws Exception {
        mockMvc.perform(get("/api/v1/timetables/search")
                        .param("sourceStationCode", "SUR")
                        .param("destinationStationCode", "PUNE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount").value(0))
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    @DisplayName("timetable search with an unknown station code returns 404")
    void timetableSearchWithUnknownStationReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/timetables/search")
                        .param("sourceStationCode", "PUNE")
                        .param("destinationStationCode", "NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Station not found with code: NOPE"));
    }

    @Test
    @DisplayName("timetable search with the same source and destination returns 400")
    void timetableSearchWithSameStationReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/timetables/search")
                        .param("sourceStationCode", "PUNE")
                        .param("destinationStationCode", "pune"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source and destination stations must be different"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long createStation(String code, String name, String city) {
        Station station = new Station();
        station.setStationCode(code);
        station.setName(name);
        station.setCity(city);
        station.setActive(true);

        return stationRepository.save(station).getId();
    }

    private long createTrain(String number, String name, String type,
                             long sourceStationId, long destinationStationId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/trains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainBody(number, name, type, sourceStationId, destinationStationId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private long addStop(long trainId, long stationId, int sequence,
                         String arrivalTime, String departureTime,
                         int dayOffset, int distanceKm) throws Exception {
        String response = mockMvc.perform(post("/api/v1/trains/{trainId}/stops", trainId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stopBody(stationId, sequence, arrivalTime, departureTime, dayOffset, distanceKm)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static String trainBody(String number, String name, String type,
                                    long sourceStationId, long destinationStationId) {
        return """
                {"trainNumber":"%s","name":"%s","trainType":"%s","sourceStationId":%d,"destinationStationId":%d}
                """.formatted(number, name, type, sourceStationId, destinationStationId);
    }

    private static String stopBody(long stationId, int sequence, String arrivalTime,
                                   String departureTime, int dayOffset, int distanceKm) {
        return """
                {"stationId":%d,"stopSequence":%d,"arrivalTime":%s,"departureTime":%s,"dayOffset":%d,"distanceFromSourceKm":%d}
                """.formatted(stationId, sequence, jsonTime(arrivalTime), jsonTime(departureTime), dayOffset, distanceKm);
    }

    private static String jsonTime(String time) {
        return time == null ? "null" : "\"" + time + "\"";
    }
}
