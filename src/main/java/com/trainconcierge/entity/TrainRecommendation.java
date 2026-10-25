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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A deterministic alternative-train suggestion for one affected journey leg.
 *
 * <p>Recommendations are historical records: regeneration never deletes them, it
 * only moves the lifecycle forward (AVAILABLE -&gt; EXPIRED). A recommendation is
 * never applied to the journey automatically.</p>
 *
 * <p>{@code activeKey} is a derived uniqueness key that is present only while the
 * recommendation is AVAILABLE and {@code null} once EXPIRED. It backs the
 * database-level "one available recommendation per journey, leg, impact and
 * candidate train" guarantee (see
 * {@code V8__alternative_train_recommendations.sql}).</p>
 *
 * <p>{@code score} is the deterministic ranking value produced by
 * {@code RecommendationScoringService}: lower is better. It is computed only
 * from observed data (timetable times and simulated running status), never from
 * random values.</p>
 */
@Entity
@Table(name = "train_recommendations")
public class TrainRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    /** The affected leg; {@code null} only for journey-level impacts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_leg_id")
    private JourneyLeg journeyLeg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_impact_id", nullable = false)
    private JourneyImpact journeyImpact;

    /** The disruption that caused the impact, when known. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disruption_event_id")
    private DisruptionEvent disruptionEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false, length = 30)
    private ImpactType impactType;

    /** The candidate replacement train. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "departure_day_offset")
    private Integer departureDayOffset;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "arrival_day_offset")
    private Integer arrivalDayOffset;

    @Column(name = "transfer_count", nullable = false)
    private int transferCount;

    /**
     * Estimated extra minutes versus the passenger's planned leg. Negative means
     * the alternative is faster. {@code null} when it cannot be computed from the
     * available timetable data.
     */
    @Column(name = "additional_minutes")
    private Integer additionalMinutes;

    /** Deterministic ranking value; lower is better. */
    @Column(name = "score", nullable = false)
    private long score;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_status", nullable = false, length = 20)
    private RecommendationStatus recommendationStatus = RecommendationStatus.AVAILABLE;

    /** Whether a simulated live status exists for the candidate on the service date. */
    @Column(name = "status_known", nullable = false)
    private boolean statusKnown;

    /** The candidate's known simulated status, or {@code null} when unknown. */
    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_status", length = 20)
    private TrainRunningStatus candidateStatus;

    /** The candidate's known delay, or {@code null} when unknown. */
    @Column(name = "candidate_delay_minutes")
    private Integer candidateDelayMinutes;

    @Column(name = "explanation", nullable = false, length = 500)
    private String explanation;

    /** Derived uniqueness key; {@code null} once the recommendation is EXPIRED. */
    @Column(name = "active_key", length = 190)
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

    public JourneyImpact getJourneyImpact() {
        return journeyImpact;
    }

    public void setJourneyImpact(JourneyImpact journeyImpact) {
        this.journeyImpact = journeyImpact;
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

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public Integer getDepartureDayOffset() {
        return departureDayOffset;
    }

    public void setDepartureDayOffset(Integer departureDayOffset) {
        this.departureDayOffset = departureDayOffset;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getArrivalDayOffset() {
        return arrivalDayOffset;
    }

    public void setArrivalDayOffset(Integer arrivalDayOffset) {
        this.arrivalDayOffset = arrivalDayOffset;
    }

    public int getTransferCount() {
        return transferCount;
    }

    public void setTransferCount(int transferCount) {
        this.transferCount = transferCount;
    }

    public Integer getAdditionalMinutes() {
        return additionalMinutes;
    }

    public void setAdditionalMinutes(Integer additionalMinutes) {
        this.additionalMinutes = additionalMinutes;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public RecommendationStatus getRecommendationStatus() {
        return recommendationStatus;
    }

    public void setRecommendationStatus(RecommendationStatus recommendationStatus) {
        this.recommendationStatus = recommendationStatus;
    }

    public boolean isStatusKnown() {
        return statusKnown;
    }

    public void setStatusKnown(boolean statusKnown) {
        this.statusKnown = statusKnown;
    }

    public TrainRunningStatus getCandidateStatus() {
        return candidateStatus;
    }

    public void setCandidateStatus(TrainRunningStatus candidateStatus) {
        this.candidateStatus = candidateStatus;
    }

    public Integer getCandidateDelayMinutes() {
        return candidateDelayMinutes;
    }

    public void setCandidateDelayMinutes(Integer candidateDelayMinutes) {
        this.candidateDelayMinutes = candidateDelayMinutes;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
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
