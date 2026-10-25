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
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * How one disruption event affects one journey.
 *
 * <p>Impacts are historical records: analysis never deletes them, it only moves
 * the lifecycle forward (ACTIVE -&gt; RESOLVED).</p>
 *
 * <p>{@code journeyLeg} is {@code null} for journey-level impacts
 * ({@link ImpactType#ITINERARY_DISRUPTED}).</p>
 *
 * <p>{@code activeKey} is a derived uniqueness key that is present only while the
 * impact is ACTIVE and {@code null} once resolved. It backs the database-level
 * "one active impact per journey, leg, event and type" guarantee (see
 * {@code V7__journey_impact_analysis.sql}).</p>
 */
@Entity
@Table(name = "journey_impacts")
public class JourneyImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    /** Null for journey-level impacts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_leg_id")
    private JourneyLeg journeyLeg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disruption_event_id", nullable = false)
    private DisruptionEvent disruptionEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false, length = 30)
    private ImpactType impactType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ImpactSeverity severity;

    /** Known only for delay/cancellation-driven impacts; {@code null} otherwise. */
    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "explanation", nullable = false, length = 500)
    private String explanation;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "last_observed_at", nullable = false)
    private OffsetDateTime lastObservedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_state", nullable = false, length = 20)
    private ImpactState impactState = ImpactState.ACTIVE;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    /** Derived uniqueness key; {@code null} once the impact is resolved. */
    @Column(name = "active_key", length = 160)
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

    public Journey getJourney() {
        return journey;
    }

    public void setJourney(Journey journey) {
        this.journey = journey;
    }

    public JourneyLeg getJourneyLeg() {
        return journeyLeg;
    }

    public void setJourneyLeg(JourneyLeg journeyLeg) {
        this.journeyLeg = journeyLeg;
    }

    public DisruptionEvent getDisruptionEvent() {
        return disruptionEvent;
    }

    public void setDisruptionEvent(DisruptionEvent disruptionEvent) {
        this.disruptionEvent = disruptionEvent;
    }

    public ImpactType getImpactType() {
        return impactType;
    }

    public void setImpactType(ImpactType impactType) {
        this.impactType = impactType;
    }

    public ImpactSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(ImpactSeverity severity) {
        this.severity = severity;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
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

    public ImpactState getImpactState() {
        return impactState;
    }

    public void setImpactState(ImpactState impactState) {
        this.impactState = impactState;
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
