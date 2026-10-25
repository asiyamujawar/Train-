package com.trainconcierge.service;

import java.util.Comparator;
import org.springframework.stereotype.Service;

/**
 * Deterministic ranking of alternative-train candidates (Phase 6).
 *
 * <p>This class contains the whole ranking policy so that it can be unit-tested
 * without a database. It never uses randomness, wall-clock time, AI or any
 * external service: two runs over the same candidate list always produce the
 * same order.</p>
 *
 * <p><strong>Priority order</strong> (as required by the Phase 6 specification),
 * from most to least important:</p>
 * <ol>
 *   <li>valid origin-to-destination coverage (a full replacement beats a partial
 *       one);</li>
 *   <li>feasible departure/arrival — enforced as an <em>eligibility filter</em>
 *       before scoring, so every scored candidate already satisfies it;</li>
 *   <li>lowest estimated additional journey time;</li>
 *   <li>fewer transfers;</li>
 *   <li>a known operational status beats an unknown one, all else equal;</li>
 *   <li>lower known delay, all else equal.</li>
 * </ol>
 *
 * <p><strong>Score</strong>: the numeric value returned by {@link #score} encodes
 * exactly the priority order above using dominating weights, so a lower score is
 * always better and the weights never let a lower-priority factor overturn a
 * higher-priority one:</p>
 * <pre>
 *   score = (partialCoverage ? 1_000_000_000_000_000 : 0)
 *         + additionalMinutes(0..999_999, unknown = 999_999) * 1_000_000_000
 *         + transferCount(0..) * 100_000_000
 *         + (statusKnown ? 0 : 10_000_000)
 *         + knownDelayMinutes(0..9_999_999)
 * </pre>
 *
 * <p><strong>Tie-breaker</strong>: when two candidates share the same score they
 * are ordered by train number and then by train id, which guarantees a stable,
 * reproducible order even for otherwise identical candidates.</p>
 */
@Service
public class RecommendationScoringService {

    /** Extra score applied when the candidate does not fully cover the leg. */
    static final long PARTIAL_COVERAGE_PENALTY = 1_000_000_000_000_000L;

    /** Score contributed by one additional minute of travel. */
    static final long PER_ADDITIONAL_MINUTE = 1_000_000_000L;

    /** Score contributed by one required transfer. */
    static final long PER_TRANSFER = 100_000_000L;

    /** Score applied when the candidate has no known operational status. */
    static final long UNKNOWN_STATUS_PENALTY = 10_000_000L;

    /** Upper bound used for a computable additional-time value. */
    static final int MAX_ADDITIONAL_MINUTES = 999_999;

    /** Upper bound used for a known delay, keeping it smaller than the status penalty. */
    static final int MAX_DELAY_MINUTES = 9_999_999;

    /**
     * Computes the deterministic ranking value of one candidate. Lower is better.
     */
    public long score(RecommendationCandidate candidate) {
        long score = candidate.fullCoverage() ? 0L : PARTIAL_COVERAGE_PENALTY;

        int minutes = candidate.additionalMinutes() == null
                ? MAX_ADDITIONAL_MINUTES
                : Math.min(Math.max(candidate.additionalMinutes(), 0), MAX_ADDITIONAL_MINUTES);
        score += (long) minutes * PER_ADDITIONAL_MINUTE;

        score += (long) Math.max(candidate.transferCount(), 0) * PER_TRANSFER;

        if (!candidate.statusKnown()) {
            score += UNKNOWN_STATUS_PENALTY;
        }

        int delay = candidate.candidateDelayMinutes() == null
                ? 0
                : Math.min(Math.max(candidate.candidateDelayMinutes(), 0), MAX_DELAY_MINUTES);
        score += delay;

        return score;
    }

    /**
     * The authoritative ordering used to sort candidates. Matches {@link #score}
     * exactly and adds the documented tie-breaker.
     */
    public Comparator<RecommendationCandidate> ranking() {
        return Comparator
                .comparingLong(this::score)
                .thenComparing(candidate -> candidate.train().getTrainNumber())
                .thenComparing(candidate -> candidate.train().getId());
    }
}
