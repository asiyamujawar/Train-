package com.trainconcierge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling and registers the Phase 4 configuration properties.
 * The scheduled monitor itself lives in
 * {@code com.trainconcierge.scheduler.DisruptionMonitoringScheduler}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(TrainConciergeProperties.class)
public class DisruptionMonitoringConfig {
}
