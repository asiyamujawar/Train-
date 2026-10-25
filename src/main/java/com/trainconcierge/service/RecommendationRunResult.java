package com.trainconcierge.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counters describing the outcome of one alternative-train recommendation cycle.
 *
 * @param impactsInspected         impacts reconciled by this cycle
 * @param candidatesEvaluated      candidate trains looked at across those impacts
 * @param recommendationsCreated   new AVAILABLE recommendation rows inserted
 * @param recommendationsUpdated   AVAILABLE rows whose ranking details changed
 * @param recommendationsExpired   AVAILABLE rows that are no longer feasible
 * @param recommendationsUnchanged AVAILABLE rows confirmed without a meaningful change
 * @param recommendationsReturned  AVAILABLE recommendations currently offered
 * @param failures                 impacts whose reconciliation failed (the rest still commit)
 * @param exclusionReasons         how many candidates were rejected, per reason code
 * @param reason                   why nothing was returned, or {@code null} when something was
 */
public record RecommendationRunResult(
        int impactsInspected,
        int candidatesEvaluated,
        int recommendationsCreated,
        int recommendationsUpdated,
        int recommendationsExpired,
        int recommendationsUnchanged,
        int recommendationsReturned,
        int failures,
        Map<String, Integer> exclusionReasons,
        String reason
) {

    public static RecommendationRunResult empty() {
        return new RecommendationRunResult(0, 0, 0, 0, 0, 0, 0, 0, new LinkedHashMap<>(), null);
    }

    /**
     * Aggregates a batch cycle by summing the counters of its per-impact
     * outcomes. The combined {@code reason} is {@code null} as soon as anything
     * was returned, so a single productive impact is not hidden by empty ones.
     */
    public RecommendationRunResult merge(RecommendationRunResult other) {
        if (other == null) {
            return this;
        }

        Map<String, Integer> mergedReasons = new LinkedHashMap<>(exclusionReasons);
        other.exclusionReasons().forEach((key, value) -> mergedReasons.merge(key, value, Integer::sum));

        int totalReturned = recommendationsReturned + other.recommendationsReturned();
        int totalFailures = failures + other.failures();
        int totalInspected = impactsInspected + other.impactsInspected();

        String mergedReason;
        if (totalReturned > 0) {
            mergedReason = null;
        } else if (totalInspected == 0) {
            mergedReason = "NO_ELIGIBLE_ACTIVE_IMPACT";
        } else if (totalFailures == totalInspected) {
            mergedReason = "ALL_IMPACTS_FAILED";
        } else {
            mergedReason = other.reason() != null ? other.reason() : reason;
            if (mergedReason == null) {
                mergedReason = "NO_FEASIBLE_ALTERNATIVES";
            }
        }

        return new RecommendationRunResult(
                totalInspected,
                candidatesEvaluated + other.candidatesEvaluated(),
                recommendationsCreated + other.recommendationsCreated(),
                recommendationsUpdated + other.recommendationsUpdated(),
                recommendationsExpired + other.recommendationsExpired(),
                recommendationsUnchanged + other.recommendationsUnchanged(),
                totalReturned,
                totalFailures,
                mergedReasons,
                mergedReason);
    }
}
