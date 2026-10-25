package com.trainconcierge.journey;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.entity.Station;
import com.trainconcierge.repository.StationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JourneyApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationRepository stationRepository;

    @Test
    @DisplayName("POST /api/v1/journeys creates a valid single-leg journey")
    void createValidSingleLegJourney() throws Exception {
        long passengerId = createPassenger("John Doe", "john@example.com");
        long s1 = createStation("JS1", "Station 1", "City A");
        long s2 = createStation("JS2", "Station 2", "City B");
        long trainId = createTrain("90001", "Train 1", "EXPRESS", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0, 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0, 100);

        String body = """
                {
                  "passengerId": %d,
                  "journeyDate": "%s",
                  "legs": [
                    { "trainId": %d, "sourceStationId": %d, "destinationStationId": %d, "legSequence": 1 }
                  ]
                }
                """.formatted(passengerId, LocalDate.now().plusDays(1), trainId, s1, s2);

        mockMvc.perform(post("/api/v1/journeys").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.journeyReference").value(org.hamcrest.Matchers.startsWith("TC-")))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.legs", hasSize(1)))
                .andExpect(jsonPath("$.legs[0].departureTime").value("08:00:00"))
                .andExpect(jsonPath("$.legs[0].arrivalTime").value("10:00:00"));
    }

    @Test
    @DisplayName("a journey with disconnected legs is rejected with 400")
    void disconnectedLegsRejected() throws Exception {
        long passengerId = createPassenger("John Doe", "john2@example.com");
        long s1 = createStation("JS11", "S1", "C1");
        long s2 = createStation("JS12", "S2", "C2");
        long s3 = createStation("JS13", "S3", "C3");
        long s4 = createStation("JS14", "S4", "C4");

        long t1 = createTrain("90002", "T1", "EXPRESS", s1, s2);
        addStop(t1, s1, 1, null, "08:00:00", 0, 0);
        addStop(t1, s2, 2, "10:00:00", null, 0, 100);

        long t2 = createTrain("90003", "T2", "EXPRESS", s3, s4);
        addStop(t2, s3, 1, null, "11:00:00", 0, 0);
        addStop(t2, s4, 2, "13:00:00", null, 0, 100);

        String body = """
                {
                  "passengerId": %d,
                  "journeyDate": "%s",
                  "legs": [
                    { "trainId": %d, "sourceStationId": %d, "destinationStationId": %d, "legSequence": 1 },
                    { "trainId": %d, "sourceStationId": %d, "destinationStationId": %d, "legSequence": 2 }
                  ]
                }
                """.formatted(passengerId, LocalDate.now().plusDays(1), t1, s1, s2, t2, s3, s4);

        mockMvc.perform(post("/api/v1/journeys").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Leg 2 source must match previous leg destination"));
    }

    @Test
    @DisplayName("a journey leg with stations in wrong order is rejected with 400")
    void wrongStationOrderRejected() throws Exception {
        long passengerId = createPassenger("John Doe", "john3@example.com");
        long s1 = createStation("JS21", "S1", "C1");
        long s2 = createStation("JS22", "S2", "C2");
        long trainId = createTrain("90004", "T1", "EXPRESS", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0, 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0, 100);

        // Try S2 to S1 (reversed)
        String body = """
                {
                  "passengerId": %d,
                  "journeyDate": "%s",
                  "legs": [
                    { "trainId": %d, "sourceStationId": %d, "destinationStationId": %d, "legSequence": 1 }
                  ]
                }
                """.formatted(passengerId, LocalDate.now().plusDays(1), trainId, s2, s1);

        mockMvc.perform(post("/api/v1/journeys").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source station must appear before destination station in train 90004"));
    }

    @Test
    @DisplayName("a journey in the past is rejected with 400")
    void pastJourneyDateRejected() throws Exception {
        long passengerId = createPassenger("John Doe", "john4@example.com");
        String body = """
                {
                  "passengerId": %d,
                  "journeyDate": "2000-01-01",
                  "legs": [
                    { "trainId": 1, "sourceStationId": 1, "destinationStationId": 2, "legSequence": 1 }
                  ]
                }
                """.formatted(passengerId);

        mockMvc.perform(post("/api/v1/journeys").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Journey date cannot be in the past"));
    }

    @Test
    @DisplayName("PATCH /api/v1/journeys/{id}/cancel sets status to CANCELLED")
    void cancelJourney() throws Exception {
        long passengerId = createPassenger("John Doe", "john5@example.com");
        long s1 = createStation("JS31", "S1", "C1");
        long s2 = createStation("JS32", "S2", "C2");
        long trainId = createTrain("90005", "T1", "EXPRESS", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0, 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0, 100);

        String body = """
                {
                  "passengerId": %d,
                  "journeyDate": "%s",
                  "legs": [
                    { "trainId": %d, "sourceStationId": %d, "destinationStationId": %d, "legSequence": 1 }
                  ]
                }
                """.formatted(passengerId, LocalDate.now().plusDays(1), trainId, s1, s2);

        String response = mockMvc.perform(post("/api/v1/journeys").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long journeyId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/v1/journeys/{id}/cancel", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long createPassenger(String name, String email) throws Exception {
        String body = """
                {"fullName":"%s", "email":"%s"}
                """.formatted(name, email);
        String response = mockMvc.perform(post("/api/v1/passengers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createStation(String code, String name, String city) {
        Station station = new Station();
        station.setStationCode(code);
        station.setName(name);
        station.setCity(city);
        station.setActive(true);
        return stationRepository.save(station).getId();
    }

    private long createTrain(String number, String name, String type, long src, long dst) throws Exception {
        String body = """
                {"trainNumber":"%s","name":"%s","trainType":"%s","sourceStationId":%d,"destinationStationId":%d}
                """.formatted(number, name, type, src, dst);
        String response = mockMvc.perform(post("/api/v1/trains").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void addStop(long trainId, long stationId, int seq, String arr, String dep, int offset, int dist) throws Exception {
        String arrJson = arr == null ? "null" : "\"" + arr + "\"";
        String depJson = dep == null ? "null" : "\"" + dep + "\"";
        String body = """
                {"stationId":%d,"stopSequence":%d,"arrivalTime":%s,"departureTime":%s,"dayOffset":%d,"distanceFromSourceKm":%d}
                """.formatted(stationId, seq, arrJson, depJson, offset, dist);
        mockMvc.perform(post("/api/v1/trains/{trainId}/stops", trainId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
