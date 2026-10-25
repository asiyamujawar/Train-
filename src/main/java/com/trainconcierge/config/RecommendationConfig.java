package com.trainconcierge.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 6 wiring for the alternative-train recommendation engine.
 *
 * <p>The recommendation engine compares candidate departures with the current
 * date/time (to reject departures that have already left). That comparison is
 * done through an injectable {@link Clock} instead of a direct call to
 * {@code LocalDate.now()} / {@code LocalTime.now()} so tests can supply a fixed
 * clock and stay deterministic. The clock is the system default zone clock in
 * normal operation.</p>
 *
 * <p>Phase 6 configuration properties themselves are bound by
 * {@link TrainConciergeProperties} (already enabled in
 * {@link DisruptionMonitoringConfig}).</p>
 */
@Configuration
public class RecommendationConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
