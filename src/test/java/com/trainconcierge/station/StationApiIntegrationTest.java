package com.trainconcierge.station;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Station API tests against the real application and the real MySQL schema
 * (Flyway V1-V3). The test transaction is rolled back, so no test data is left
 * behind. MySQL must be running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/stations creates a station, normalises the code and returns 201")
    void createStationReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"zz07","name":"Zeta Junction","city":"Zetaville","state":"Zetaland"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.stationCode").value("ZZ07"))
                .andExpect(jsonPath("$.name").value("Zeta Junction"))
                .andExpect(jsonPath("$.city").value("Zetaville"))
                .andExpect(jsonPath("$.state").value("Zetaland"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/stations rejects a duplicate station code with 409")
    void duplicateStationCodeReturnsConflict() throws Exception {
        String body = """
                {"stationCode":"ZZDUP","name":"Duplicate Junction","city":"Duplicatetown"}
                """;

        mockMvc.perform(post("/api/v1/stations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/stations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Station code 'ZZDUP' already exists"))
                .andExpect(jsonPath("$.path").value("/api/v1/stations"));
    }

    @Test
    @DisplayName("a station can be retrieved by id and by station code")
    void getStationByIdAndByCode() throws Exception {
        String response = mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"ZZ08","name":"Eta Junction","city":"Etaville"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/v1/stations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationCode").value("ZZ08"));

        mockMvc.perform(get("/api/v1/stations/code/{stationCode}", "zz08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("a missing station returns 404 with the standard error body")
    void missingStationReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/stations/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Station not found with id: 999999999"))
                .andExpect(jsonPath("$.path").value("/api/v1/stations/999999999"));

        mockMvc.perform(get("/api/v1/stations/code/{stationCode}", "NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Station not found with code: NOPE"));
    }

    @Test
    @DisplayName("an invalid station body returns 400 with per-field errors")
    void invalidStationBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"","name":"","city":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.fieldErrors.stationCode").exists())
                .andExpect(jsonPath("$.fieldErrors.name").value("name must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.city").value("city must not be blank"));
    }

    @Test
    @DisplayName("GET /api/v1/stations filters, paginates and sorts")
    void searchStationsWithFiltersAndPagination() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/stations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"stationCode":"ZZ1%d","name":"Filter Junction %d","city":"Filterville"}
                                    """.formatted(i, i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/stations")
                        .param("city", "Filterville")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "stationCode,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].stationCode").value("ZZ11"))
                .andExpect(jsonPath("$.content[1].stationCode").value("ZZ12"));
    }

    @Test
    @DisplayName("PATCH /api/v1/stations/{id}/deactivate flips the active flag")
    void deactivateStation() throws Exception {
        String response = mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"ZZ09","name":"Theta Junction","city":"Thetaville"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/v1/stations/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
