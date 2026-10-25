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

/**
 * An immutable, append-only record of one status transition. A row is written
 * for the initial status and for every meaningful change afterwards; existing
 * rows are never updated or deleted.
 *
 * <p>The {@code previous*} fields are {@code null} only for the initial record.</p>
 */
@Entity
@Table(name = "train_status_history")
public class TrainStatusHistory {

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
    @Column(name = "previous_status", length = 20)
    private TrainRunningStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private TrainRunningStatus newStatus;

    @Column(name = "previous_delay_minutes")
    private Integer previousDelayMinutes;

    @Column(name = "new_delay_minutes", nullable = false)
    private int newDelayMinutes;

    @Column(name = "reason", length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_station_id")
    private Station currentStation;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private StatusSource source;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

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

    public TrainRunningStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(TrainRunningStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public TrainRunningStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(TrainRunningStatus newStatus) {
        this.newStatus = newStatus;
    }

    public Integer getPreviousDelayMinutes() {
        return previousDelayMinutes;
    }

    public void setPreviousDelayMinutes(Integer previousDelayMinutes) {
        this.previousDelayMinutes = previousDelayMinutes;
    }

    public int getNewDelayMinutes() {
        return newDelayMinutes;
    }

    public void setNewDelayMinutes(int newDelayMinutes) {
        this.newDelayMinutes = newDelayMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Station getCurrentStation() {
        return currentStation;
    }

    public void setCurrentStation(Station currentStation) {
        this.currentStation = currentStation;
    }

    public StatusSource getSource() {
        return source;
    }

    public void setSource(StatusSource source) {
        this.source = source;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(OffsetDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
