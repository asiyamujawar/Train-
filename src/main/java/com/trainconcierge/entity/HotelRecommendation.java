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
 * One historically-recorded hotel recommendation for a journey.
 *
 * <p>Rows are never deleted: repeated evaluations refresh the AVAILABLE rows,
 * mark no-longer-suitable ones {@link RecommendationStatus#EXPIRED} and release
 * their {@link #activeKey}, so history is preserved while the current result
 * set stays unique (see {@code V9__hotel_recommendations.sql}).</p>
 */
@Entity
@Table(name = "hotel_recommendations")
public class HotelRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    /** The leg impact that forced the overnight decision; {@code null} when none. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_impact_id")
    private JourneyImpact journeyImpact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_outcome", nullable = false, length = 30)
    private HotelDecisionOutcome decisionOutcome;

    /** Station where the passenger is stranded and the hotel is located. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relevant_station_id")
    private Station relevantStation;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_status", nullable = false, length = 20)
    private RecommendationStatus recommendationStatus = RecommendationStatus.AVAILABLE;

    /** Derived uniqueness key; {@code null} once the row is EXPIRED. */
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

    public JourneyImpact getJourneyImpact() {
        return journeyImpact;
    }

    public void setJourneyImpact(JourneyImpact journeyImpact) {
        this.journeyImpact = journeyImpact;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
    }

    public HotelDecisionOutcome getDecisionOutcome() {
        return decisionOutcome;
    }

    public void setDecisionOutcome(HotelDecisionOutcome decisionOutcome) {
        this.decisionOutcome = decisionOutcome;
    }

    public Station getRelevantStation() {
        return relevantStation;
    }

    public void setRelevantStation(Station relevantStation) {
        this.relevantStation = relevantStation;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(int rankPosition) {
        this.rankPosition = rankPosition;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public RecommendationStatus getRecommendationStatus() {
        return recommendationStatus;
    }

    public void setRecommendationStatus(RecommendationStatus recommendationStatus) {
        this.recommendationStatus = recommendationStatus;
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
