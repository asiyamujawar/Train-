package com.trainconcierge.scheduler;

import com.trainconcierge.service.DisruptionMonitoringService;
import com.trainconcierge.service.MonitoringRunResult;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin scheduled trigger for {@link DisruptionMonitoringService}.
 *
 * <p>Uses fixed-delay scheduling so a long run cannot overlap with the next one.
 * An additional {@link ReentrantLock} guards against overlap within this
 * application instance (a manual {@code POST /api/v1/monitoring/run} racing the
 * scheduled run). Set {@code trainconcierge.monitoring.enabled=false} to disable
 * it entirely in tests and local debugging.</p>
 */
@Component
@ConditionalOnProperty(prefix = "trainconcierge.monitoring", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DisruptionMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(DisruptionMonitoringScheduler.class);

    private final DisruptionMonitoringService disruptionMonitoringService;
    private final ReentrantLock lock = new ReentrantLock();

    public DisruptionMonitoringScheduler(DisruptionMonitoringService disruptionMonitoringService) {
        this.disruptionMonitoringService = disruptionMonitoringService;
    }

    @Scheduled(fixedDelayString = "${trainconcierge.monitoring.fixed-delay-ms:60000}")
    public void monitorDisruptions() {
        if (!lock.tryLock()) {
            log.warn("Skipping disruption monitoring run: a previous run is still in progress");
            return;
        }
        try {
            MonitoringRunResult result = disruptionMonitoringService.runMonitoring();
            log.info("Scheduled disruption monitoring completed: {}", result);
        } catch (Exception ex) {
            // Never let a monitoring failure kill the scheduler thread.
            log.error("Scheduled disruption monitoring failed: {}", ex.getMessage(), ex);
        } finally {
            lock.unlock();
        }
    }
}
