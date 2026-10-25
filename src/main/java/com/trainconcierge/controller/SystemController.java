package com.trainconcierge.controller;

import com.trainconcierge.dto.EchoRequest;
import com.trainconcierge.dto.EchoResponse;
import com.trainconcierge.dto.SystemStatusResponse;
import com.trainconcierge.service.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 0 foundation endpoints: application/database status and a validation demo.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Foundation endpoints for Phase 0")
public class SystemController {

    private final SystemStatusService systemStatusService;

    public SystemController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @Operation(summary = "Application and MySQL connection status")
    @GetMapping("/status")
    public SystemStatusResponse status() {
        return systemStatusService.getStatus();
    }

    @Operation(summary = "Echo a validated message (proves Bean Validation + error handling)")
    @PostMapping("/echo")
    public EchoResponse echo(@Valid @RequestBody EchoRequest request) {
        return new EchoResponse(request.message(), request.message().length(), OffsetDateTime.now());
    }
}
