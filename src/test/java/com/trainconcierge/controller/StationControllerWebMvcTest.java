package com.trainconcierge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trainconcierge.dto.StationRequest;
import com.trainconcierge.dto.StationResponse;
import com.trainconcierge.exception.ConflictException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.service.StationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for the Phase 1 station endpoints. This slice does not start
 * JPA or Flyway, so it runs without a MySQL server: the service is mocked and
 * only the HTTP contract, validation and error mapping are exercised.
 */
@WebMvcTest(controllers = StationController.class)
class StationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StationService stationService;

    @Test
    @DisplayName("POST /api/v1/stations returns 201 with a Location header")
    void createReturnsCreated() throws Exception {
        given(stationService.create(any(StationRequest.class))).willReturn(
                new StationResponse(7L, "ZZ01", "Zeta Junction", "Zetaville", "Zetaland", true, null, null));

        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"ZZ01","name":"Zeta Junction","city":"Zetaville","state":"Zetaland"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/stations/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.stationCode").value("ZZ01"));
    }

    @Test
    @DisplayName("POST /api/v1/stations returns 400 for a blank body")
    void createReturnsBadRequestForBlankBody() throws Exception {
        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"","name":"","city":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.path").value("/api/v1/stations"))
                .andExpect(jsonPath("$.fieldErrors.stationCode").exists())
                .andExpect(jsonPath("$.fieldErrors.name").value("name must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.city").value("city must not be blank"));
    }

    @Test
    @DisplayName("POST /api/v1/stations maps a duplicate code to 409")
    void createMapsDuplicateToConflict() throws Exception {
        given(stationService.create(any(StationRequest.class)))
                .willThrow(new ConflictException("Station code 'ZZ01' already exists"));

        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"ZZ01","name":"Zeta Junction","city":"Zetaville"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Station code 'ZZ01' already exists"));
    }

    @Test
    @DisplayName("GET /api/v1/stations/{id} maps a missing station to 404")
    void getByIdMapsMissingToNotFound() throws Exception {
        given(stationService.getById(eq(42L)))
                .willThrow(new ResourceNotFoundException("Station not found with id: 42"));

        mockMvc.perform(get("/api/v1/stations/{id}", 42))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Station not found with id: 42"))
                .andExpect(jsonPath("$.path").value("/api/v1/stations/42"));
    }

    @Test
    @DisplayName("GET /api/v1/stations/{id} returns the station")
    void getByIdReturnsStation() throws Exception {
        given(stationService.getById(eq(7L))).willReturn(
                new StationResponse(7L, "ZZ01", "Zeta Junction", "Zetaville", null, true, null, null));

        mockMvc.perform(get("/api/v1/stations/{id}", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationCode").value("ZZ01"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("a non-numeric station id returns 400")
    void nonNumericIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stations/{id}", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value"));
    }
}
