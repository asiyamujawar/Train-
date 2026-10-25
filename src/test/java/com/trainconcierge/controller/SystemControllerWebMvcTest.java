package com.trainconcierge.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trainconcierge.dto.SystemStatusResponse;
import com.trainconcierge.service.SystemStatusService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests. This slice does not start JPA/Flyway, so it runs without a
 * MySQL server.
 */
@WebMvcTest(controllers = SystemController.class)
class SystemControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemStatusService systemStatusService;

    @Test
    @DisplayName("GET /api/v1/system/status returns application and database information")
    void statusReturnsApplicationAndDatabaseInformation() throws Exception {
        given(systemStatusService.getStatus()).willReturn(new SystemStatusResponse(
                "trainconcierge",
                "0.0.1-SNAPSHOT",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                true,
                "MySQL",
                "8.0.46"));

        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("trainconcierge"))
                .andExpect(jsonPath("$.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.databaseConnected").value(true))
                .andExpect(jsonPath("$.databaseProduct").value("MySQL"))
                .andExpect(jsonPath("$.databaseVersion").value("8.0.46"));
    }

    @Test
    @DisplayName("POST /api/v1/system/echo returns the validated message")
    void echoReturnsValidatedMessage() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello"))
                .andExpect(jsonPath("$.messageLength").value(5))
                .andExpect(jsonPath("$.processedAt").exists());
    }

    @Test
    @DisplayName("POST /api/v1/system/echo rejects a blank message with the standard error body")
    void echoRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.path").value("/api/v1/system/echo"))
                .andExpect(jsonPath("$.fieldErrors.message").value("message must not be blank"));
    }

    @Test
    @DisplayName("POST /api/v1/system/echo rejects a malformed body")
    void echoRejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"))
                .andExpect(jsonPath("$.path").value("/api/v1/system/echo"));
    }

    @Test
    @DisplayName("an unknown path returns the standard 404 body")
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/unknown"));
    }

    @Test
    @DisplayName("a known path with the wrong HTTP method returns the standard 405 body")
    void wrongMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/system/echo"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.path").value("/api/v1/system/echo"));
    }
}
