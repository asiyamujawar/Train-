package com.trainconcierge.service;

import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRunningStatus;
import java.time.LocalTime;

/**
 * A candidate replacement train that already passed the Phase 6 feasibility
 * rules for one affected leg. Pure data: it carries only observed timetable
 * values and the candidate's simulated running status, never invented ones.
 *
 * @param train                  the candidate replacement train
 * @param departureTime          scheduled departure from the affected leg origin
 * @param departureDayOffset     day offset of that departure relative to the service date
 * @param arrivalTime            scheduled arrival at the affected leg destination
 * @param arrivalDayOffset       day offset of that arrival relative to the service date
 * @param fullCoverage           true when the candidate runs the whole leg origin to destination
 * @param transferCount          number of changes required to use the candidate (0 in this phase)
 * @param additionalMinutes      extra minutes versus the passenger's planned leg, or {@code null}
 *                               when it cannot be computed
 * @param statusKnown            whether a simulated running status exists for the service date
 * @param candidateStatus        the known simulated status, or {@code null}
 * @param candidateDelayMinutes  the known delay in minutes, or {@code null}
 */
public record RecommendationCandidate(
        Train train,
        LocalTime departureTime,
        Integer departureDayOffset,
        LocalTime arrivalTime,
        Integer arrivalDayOffset,
        boolean fullCoverage,
        int transferCount,
        Integer additionalMinutes,
        boolean statusKnown,
        TrainRunningStatus candidateStatus,
        Integer candidateDelayMinutes
) {
}
