package com.trainconcierge.service;

import com.trainconcierge.dto.SystemStatusResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reports the state of the running application and of its MySQL connection.
 */
@Service
public class SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final String applicationName;
    private final String applicationVersion;

    public SystemStatusService(DataSource dataSource,
                               @Value("${spring.application.name:trainconcierge}") String applicationName,
                               @Value("${info.app.version:0.0.1-SNAPSHOT}") String applicationVersion) {
        this.dataSource = dataSource;
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    public SystemStatusResponse getStatus() {
        boolean connected = false;
        String product = null;
        String version = null;

        try (Connection connection = dataSource.getConnection()) {
            connected = connection.isValid(VALIDATION_TIMEOUT_SECONDS);
            DatabaseMetaData metaData = connection.getMetaData();
            product = metaData.getDatabaseProductName();
            version = metaData.getDatabaseProductVersion();
        } catch (SQLException ex) {
            log.warn("Database connectivity check failed: {}", ex.getMessage());
        }

        return new SystemStatusResponse(
                applicationName, applicationVersion, OffsetDateTime.now(), connected, product, version);
    }
}
