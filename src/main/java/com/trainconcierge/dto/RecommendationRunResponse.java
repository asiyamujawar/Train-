package com.trainconcierge.dto;

import com.trainconcierge.service.RecommendationRunResult;
import java.util.Map;

/**
 * Response body for one alternative-train recommendation cycle. It reports what
 * was inspected, what was produced and, when nothing was produced, why.
 *
 * @param impactsInspected         impacts reconciled by this cycle
 * @param candidatesEvaluated      candidate trains looked at
 * @param recommendationsCreated   new AVAILABLE recommendations inserted
 * @param recommendationsUpdated   AVAILABLE recommendations whose details changed
 * @param recommendationsExpired   AVAILABLE recommendations that are no longer feasible
 * @param recommendationsUnchanged AVAILABLE recommendations confirmed unchanged
 * @param recommendationsReturned  AVAILABLE recommendations currently offered
 * @param failures                 impacts whose reconciliation failed
 * @param exclusionReasons         rejected candidates counted by reason code
 * @param reason                   why nothing was returned, or {@code null} when something was
 */
public record RecommendationRunResponse(
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

    public static RecommendationRunResponse from(RecommendationRunResult result) {
        return new RecommendationRunResponse(
                result.impactsInspected(),
                result.candidatesEvaluated(),
                result.recommendationsCreated(),
                result.recommendationsUpdated(),
                result.recommendationsExpired(),
                result.recommendationsUnchanged(),
                result.recommendationsReturned(),
                result.failures(),
                result.exclusionReasons(),
                result.reason());
    }
}
