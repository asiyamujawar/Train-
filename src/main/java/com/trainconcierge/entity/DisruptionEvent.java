package com.trainconcierge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A disruption automatically detected from a train's simulated running status.
 *
 * <p>Events are historical records: ordinary monitoring never deletes them, it
 * only moves the lifecycle forward (ACTIVE -&gt; ACKNOWLEDGED -&gt; RESOLVED).</p>
 *
 * <p>{@code activeKey} is a derived uniqueness key that is present only while the
 * event is ongoing and {@code null} once resolved. It backs the database-level
 * "one ongoing event per train, service date and type" guarantee (see
 * {@code V6__disruption_monitoring.sql}).</p>
 */
@Entity
@Table(name = "disruption_events")
public class DisruptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "disruption_type", nullable = false, length = 30)
    private DisruptionType disruptionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "observed_train_status", nullable = false, length = 20)
    private TrainRunningStatus observedTrainStatus;

    @Column(name = "delay_minutes", nullable = false)
    private int delayMinutes;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "last_observed_at", nullable = false)
    private OffsetDateTime lastObservedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_state", nullable = false, length = 20)
    private EventState eventState = EventState.ACTIVE;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    /** Derived uniqueness key; {@code null} once the event is resolved. */
    @Column(name = "active_key", length = 120)
    private String activeKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Train getTrain() {
        return train;
    }

    public void setTrain(Train train) {
        this.train = train;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public DisruptionType getDisruptionType() {
        return disruptionType;
    }

    public void setDisruptionType(DisruptionType disruptionType) {
        this.disruptionType = disruptionType;
    }

    public TrainRunningStatus getObservedTrainStatus() {
        return observedTrainStatus;
    }

    public void setObservedTrainStatus(TrainRunningStatus observedTrainStatus) {
        this.observedTrainStatus = observedTrainStatus;
    }

    public int getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(int delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(OffsetDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public OffsetDateTime getLastObservedAt() {
        return lastObservedAt;
    }

    public void setLastObservedAt(OffsetDateTime lastObservedAt) {
        this.lastObservedAt = lastObservedAt;
    }

    public EventState getEventState() {
        return eventState;
    }

    public void setEventState(EventState eventState) {
        this.eventState = eventState;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getActiveKey() {
        return activeKey;
    }

    public void setActiveKey(String activeKey) {
        this.activeKey = activeKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
