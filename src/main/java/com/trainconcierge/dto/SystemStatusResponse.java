package com.trainconcierge.dto;

import java.time.OffsetDateTime;

/**
 * Current state of the running application and its MySQL connection.
 *
 * @param application       value of spring.application.name
 * @param version           application version
 * @param checkedAt         server timestamp of the check
 * @param databaseConnected whether a JDBC connection could be obtained and validated
 * @param databaseProduct   database product name reported by the driver (null when unreachable)
 * @param databaseVersion   database product version reported by the driver (null when unreachable)
 */
public record SystemStatusResponse(
        String application,
        String version,
        OffsetDateTime checkedAt,
        boolean databaseConnected,
        String databaseProduct,
        String databaseVersion
) {
}
