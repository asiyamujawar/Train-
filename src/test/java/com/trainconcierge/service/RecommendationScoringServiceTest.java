package com.trainconcierge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRunningStatus;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Phase 6 ranking policy. No Spring context and no database is
 * needed, so the ranking rules are verified in isolation and deterministically.
 */
class RecommendationScoringServiceTest {

    private final RecommendationScoringService scoring = new RecommendationScoringService();

    @Test
    @DisplayName("the score is stable: the same candidate always produces the same value")
    void scoreIsStable() {
        RecommendationCandidate candidate = candidate(1L, "100001", true, 0, 0, true,
                TrainRunningStatus.ON_TIME, 0);

        assertThat(scoring.score(candidate)).isEqualTo(scoring.score(candidate));
    }

    @Test
    @DisplayName("valid origin-to-destination coverage outranks every other factor")
    void fullCoverageBeatsPartial() {
        // A partial candidate that is otherwise ideal ...
        RecommendationCandidate partial = candidate(1L, "100001", false, -120, 0, true,
                TrainRunningStatus.ON_TIME, 0);
        // ... must still lose to a full replacement that is far worse on every
        // secondary factor.
        RecommendationCandidate full = candidate(2L, "100002", true, 600, 3, false, null, null);

        assertThat(scoring.score(full)).isLessThan(scoring.score(partial));
        assertThat(rank(full, partial)).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("a shorter additional journey time outranks fewer transfers, status and delay")
    void lowestAdditionalTimeFirst() {
        RecommendationCandidate faster = candidate(1L, "100001", true, 10, 2, false, null, null);
        RecommendationCandidate slower = candidate(2L, "100002", true, 11, 0, true,
                TrainRunningStatus.ON_TIME, 0);

        assertThat(rank(faster, slower)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("fewer transfers outrank a known status and a lower delay")
    void fewerTransfersFirst() {
        RecommendationCandidate fewer = candidate(1L, "100001", true, 30, 0, false, null, null);
        RecommendationCandidate more = candidate(2L, "100002", true, 30, 1, true,
                TrainRunningStatus.ON_TIME, 0);

        assertThat(rank(fewer, more)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("a known operational status outranks an unknown one, all else equal")
    void knownStatusBeatsUnknown() {
        RecommendationCandidate known = candidate(1L, "100001", true, 30, 0, true,
                TrainRunningStatus.ON_TIME, 0);
        RecommendationCandidate unknown = candidate(2L, "100002", true, 30, 0, false, null, null);

        assertThat(rank(unknown, known)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("a lower known delay outranks a higher one, all else equal")
    void lowerDelayFirst() {
        RecommendationCandidate small = candidate(1L, "100001", true, 30, 0, true,
                TrainRunningStatus.DELAYED, 5);
        RecommendationCandidate large = candidate(2L, "100002", true, 30, 0, true,
                TrainRunningStatus.DELAYED, 90);

        assertThat(rank(large, small)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("identical candidates are ordered by train number, then by train id")
    void documentedTieBreaker() {
        RecommendationCandidate b = candidate(20L, "100002", true, 0, 0, true,
                TrainRunningStatus.ON_TIME, 0);
        RecommendationCandidate a = candidate(10L, "100001", true, 0, 0, true,
                TrainRunningStatus.ON_TIME, 0);
        // same train number as b, lower id: must come before b
        RecommendationCandidate a2 = candidate(5L, "100002", true, 0, 0, true,
                TrainRunningStatus.ON_TIME, 0);

        assertThat(scoring.score(a)).isEqualTo(scoring.score(b));
        assertThat(rank(b, a, a2)).containsExactly(10L, 5L, 20L);
    }

    @Test
    @DisplayName("a null additional time is treated as the worst computable value")
    void unknownAdditionalTimeIsPenalised() {
        RecommendationCandidate known = candidate(1L, "100001", true, 1_000, 0, true,
                TrainRunningStatus.ON_TIME, 0);
        RecommendationCandidate unknown = candidate(2L, "100002", true, null, 0, true,
                TrainRunningStatus.ON_TIME, 0);

        assertThat(rank(unknown, known)).containsExactly(1L, 2L);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<Long> rank(RecommendationCandidate... candidates) {
        return java.util.Arrays.stream(candidates)
                .sorted(scoring.ranking())
                .map(candidate -> candidate.train().getId())
                .toList();
    }

    private static RecommendationCandidate candidate(long trainId,
                                                     String trainNumber,
                                                     boolean fullCoverage,
                                                     Integer additionalMinutes,
                                                     int transferCount,
                                                     boolean statusKnown,
                                                     TrainRunningStatus status,
                                                     Integer delayMinutes) {
        Train train = new Train();
        train.setId(trainId);
        train.setTrainNumber(trainNumber);
        return new RecommendationCandidate(
                train,
                LocalTime.of(11, 0), 0,
                LocalTime.of(13, 0), 0,
                fullCoverage,
                transferCount,
                additionalMinutes,
                statusKnown,
                status,
                delayMinutes);
    }
}
