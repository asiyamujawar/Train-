package com.trainconcierge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full application context test. This test needs a reachable MySQL server and the
 * {@code trainconcierge_db} database to exist, because Flyway validates and applies
 * migrations during startup.
 */
@SpringBootTest
class TrainConciergeApplicationTests {

    @Test
    void contextLoads() {
        // Fails if Spring cannot build the context (datasource, Flyway, JPA, MVC, Actuator).
    }
}
