# TrainConcierge — Phase 6: Alternative Train Recommendations (MySQL)

## 1. Objective and implemented features

Turn the journey impacts detected in **Phase 5** into concrete, deterministic alternative-train suggestions for the affected leg. The search and the ranking are plain Java — no ML, no external AI API, no live railway API — and reuse the existing entities of Phases 1–5 (`Station`, `Train`, `TrainStop`, `Journey`, `JourneyLeg`, `TrainStatus`, `DisruptionEvent`, `JourneyImpact`). A recommendation is a **suggestion only**: it never modifies the passenger's journey, and there is no acceptance workflow.

**Implemented**

- `POST /impacts/{impactId}/recommendations/run` recomputes the recommendations of one impact and returns a run summary (candidates evaluated, recommendations returned, exclusion reasons, reason).
- `POST /recommendations/run` runs the same logic as a **bounded batch** over the ongoing impacts inside the configured journey-date window.
- `GET /journeys/{journeyId}/recommendations` and `GET /impacts/{impactId}/recommendations` return the stored recommendations (paginated, `AVAILABLE` by default, history retrievable via `recommendationStatus`).
- For a **cancelled** leg train, every other active train that serves the same origin → destination in the correct order is a candidate.
- For a **delayed** leg train (including a threatened connection), the same candidate search runs and each candidate is additionally checked against the following leg's departure plus the configured transfer buffer.
- The disrupted train itself and any candidate **cancelled for the service date** are never recommended.
- A candidate with no status row is **not** assumed to be on time: it is kept, flagged as unknown and ranked below candidates with a known status.
- Deterministic ranking in a dedicated, unit-testable class (lower score is better).
- Idempotent regeneration with preserved history (nothing is deleted), backed by a database-level unique key.
- Explicit reasons when nothing is returned: `NO_FEASIBLE_ALTERNATIVES`, `INSUFFICIENT_TIMETABLE_DATA`, `ALL_CANDIDATES_DISRUPTED`, `NO_ELIGIBLE_ACTIVE_IMPACT`, `IMPACT_NOT_LEG_SPECIFIC`.

**Key design decisions**

| # | Decision | Rationale |
| :-- | :--- | :--- |
| D1 | Only impacts with `impactState = ACTIVE` are expanded | A resolved disruption must not produce new suggestions |
| D2 | Only **leg-specific** impacts are expanded; a journey-level `ITINERARY_DISRUPTED` returns `IMPACT_NOT_LEG_SPECIFIC` | A journey-level event names no leg to replace |
| D3 | Candidates come from real `train_stops` rows: active train, origin stop before destination stop | Order, direction and "serves the leg" are taken from stored timetable data, never invented |
| D4 | A missing departure/arrival time on a candidate is a hard exclusion (`MISSING_TIMETABLE_DATA`) | A recommendation is never built from invented times |
| D5 | A candidate whose simulated status is `CANCELLED` for the service date is excluded | Never recommend a train that will not run |
| D6 | A missing status row is kept and flagged `statusKnown = false` | An unknown status is never presented as on time |
| D7 | `ALREADY_DEPARTED`: a candidate whose scheduled departure is in the past (compared through an injected `Clock`) is excluded | A departure that has passed cannot be used |
| D8 | A candidate must reach the connection station at least `min-transfer-buffer-minutes` before the next leg departs | Matches the Phase 5 transfer convention, including a single midnight crossing |
| D9 | `additionalMinutes` is computed from scheduled durations only, and is `null` when either duration is unknown | Never invent a duration |
| D10 | Recommendation identity is a derived `active_key` = `J<journeyId>:L<legId\|0>:I<impactId>:T<candidateTrainId>`, `NULL` once `EXPIRED` | A nullable `UNIQUE` column gives one `AVAILABLE` row per candidate and unlimited history (MySQL allows many `NULL`s) |
| D11 | Regeneration refreshes existing `AVAILABLE` rows and marks infeasible ones `EXPIRED` (key released), never deletes | Idempotency and auditability |
| D12 | Each impact is reconciled in its own transaction; the batch is not one big transaction | One failure cannot roll back the others and is reported in `failures` |
| D13 | Scoring/ranking lives in its own service class | Deterministic and unit-testable without a database |
| D14 | Endpoints are **unauthenticated development endpoints** | No authentication is implemented yet |

## 2. Files created / modified

**Created**

- [V8__alternative_train_recommendations.sql](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/db/migration/V8__alternative_train_recommendations.sql)
- [RecommendationStatus.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/RecommendationStatus.java) · [TrainRecommendation.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/TrainRecommendation.java)
- [TrainRecommendationRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/TrainRecommendationRepository.java)
- [RecommendationConfig.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/RecommendationConfig.java)
- [RecommendationCandidate.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/RecommendationCandidate.java) · [RecommendationRunResult.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/RecommendationRunResult.java) · [RecommendationScoringService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/RecommendationScoringService.java) · [ImpactRecommendationService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/ImpactRecommendationService.java) · [AlternativeRecommendationService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/AlternativeRecommendationService.java)
- [TrainRecommendationResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/TrainRecommendationResponse.java) · [RecommendationRunResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/RecommendationRunResponse.java)
- [RecommendationController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/RecommendationController.java)
- [RecommendationScoringServiceTest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/test/java/com/trainconcierge/service/RecommendationScoringServiceTest.java) · [RecommendationApiIntegrationTest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/test/java/com/trainconcierge/recommendation/RecommendationApiIntegrationTest.java)
- `phase6.md` (this file)

**Modified (additive only)**

- [TrainConciergeProperties.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/TrainConciergeProperties.java) — added the `recommendation` block.
- [TrainStopRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/TrainStopRepository.java) — added `findCandidateTrains`.
- [JourneyImpactRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/JourneyImpactRepository.java) — added `findActiveImpactsInJourneyDateRange`.
- [application.yml](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/application.yml) — added the `trainconcierge.recommendation` block.

No Phase 0–5 behaviour was changed and no applied migration was edited.

## 3. Route search and ranking algorithm

Both the candidate search and the ranking are deterministic; two runs over the same data always produce the same result.

### 3.1 Candidate search (eligibility + feasibility)

For an `ACTIVE`, leg-specific impact, the affected leg is `sourceStation → destinationStation` and the service date is `journey.journeyDate`.

1. **SQL candidate search** — `TrainStopRepository.findCandidateTrains(sourceStationCode, destinationStationCode)` returns `[train, sourceStop, destinationStop, departureTime, departureDayOffset, arrivalTime, arrivalDayOffset]` for every **active** train that stops at the origin **before** the destination (`src.stopSequence < dst.stopSequence`). The `stopSequence` ordering is what enforces correct direction and rejects a reversed-order train.
2. **Eligibility filters** (each rejection is counted in `exclusionReasons`):
   - `ORIGINAL_TRAIN` — the candidate is the disrupted train itself.
   - `MISSING_TIMETABLE_DATA` — the candidate's scheduled departure or arrival is `null`.
   - `CANDIDATE_CANCELLED` — the candidate has a `TrainStatus` of `CANCELLED` for the service date.
   - `ALREADY_DEPARTED` — the candidate's scheduled departure (`serviceDate + departureDayOffset`, compared through the injected `Clock`) is already in the past.
   - `CONNECTION_NOT_FEASIBLE` — the leg has a following leg and the candidate cannot arrive at the connection station at least `min-transfer-buffer-minutes` before the next leg departs. A single midnight crossing is handled by adding one day (`1440` minutes) when the next departure time-of-day is earlier than the arrival time-of-day, reusing the Phase 5 convention. If the next leg's departure time is missing → `MISSING_TIMETABLE_DATA`.
3. Surviving candidates become `RecommendationCandidate` records carrying only observed timetable values plus the candidate's simulated status (or `null`).

`additionalMinutes` = `candidateDuration − plannedLegDuration`, both derived from scheduled times (`arrival − departure`, in minutes, including day offsets). It is `null` when either duration cannot be derived. A negative value means the alternative is faster.

### 3.2 Ranking (`RecommendationScoringService`)

Priority order, most to least important:

1. **Full origin → destination coverage** beats a partial one (partial coverage carries a dominating penalty).
2. **Feasible departure/arrival** — enforced as an eligibility filter *before* scoring, so every scored candidate already satisfies it.
3. **Lowest additional journey time** (unknown additional time is treated as the maximum, i.e. worst).
4. **Fewer transfers**.
5. **Known status** beats unknown.
6. **Lower known delay**.

The numeric score encodes this exactly with dominating weights, so a lower-priority factor can never overturn a higher-priority one:

```
score = (partialCoverage ? 1_000_000_000_000_000 : 0)
      + additionalMinutes(0..999_999, unknown = 999_999) * 1_000_000_000
      + transferCount(0..)                               * 100_000_000
      + (statusKnown ? 0 : 10_000_000)
      + knownDelayMinutes(0..9_999_999)
```

**Tie-breaker**: equal scores are ordered by train number, then by train id. This guarantees a stable, reproducible order even for otherwise identical candidates.

Only the top `max-results-per-impact` candidates are stored and returned.

### 3.3 Explanations and reasons

- Every returned recommendation carries a human-readable `explanation` built from the actual data: train number/name, the leg (`sequence`, `CODE -> CODE`), the service date, the departure/arrival times (with a day marker when `dayOffset > 0`), the `+N min`/`−N min` additional time (or an explicit "could not be computed" note), the transfer count, and whether the live status is known (with status/delay) or unknown.
- When nothing is returned, the run response carries a `reason`:
  - `NO_FEASIBLE_ALTERNATIVES` — candidates were evaluated but all were infeasible (or none existed).
  - `INSUFFICIENT_TIMETABLE_DATA` — the only exclusions were missing timetable data.
  - `ALL_CANDIDATES_DISRUPTED` — every evaluated candidate was the original train or a cancelled candidate.
  - `NO_ELIGIBLE_ACTIVE_IMPACT` — the impact is not `ACTIVE`.
  - `IMPACT_NOT_LEG_SPECIFIC` — the impact is journey-level (no leg to replace).

## 4. Database changes and migration instructions

New migration: `V8__alternative_train_recommendations.sql` (`V1`–`V7` untouched). Flyway applies it automatically at startup; there is nothing to run by hand. The migration is **additive only**.

Table `train_recommendations`:

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | `BIGINT AUTO_INCREMENT` | PK |
| `journey_id` | `BIGINT NOT NULL` | FK → `journeys(id)` `ON DELETE CASCADE` |
| `journey_leg_id` | `BIGINT NULL` | FK → `journey_legs(id)` `ON DELETE CASCADE`; `NULL` for journey-level impacts |
| `journey_impact_id` | `BIGINT NOT NULL` | FK → `journey_impacts(id)` `ON DELETE CASCADE` |
| `disruption_event_id` | `BIGINT NULL` | FK → `disruption_events(id)` `ON DELETE CASCADE` (when known) |
| `impact_type` | `VARCHAR(30) NOT NULL` | `DELAY`, `CANCELLATION`, `MISSED_CONNECTION`, `ITINERARY_DISRUPTED` |
| `train_id` | `BIGINT NOT NULL` | FK → `trains(id)` `ON DELETE CASCADE`; the candidate replacement |
| `service_date` | `DATE NOT NULL` | the candidate's service date (= `journey.journeyDate`) |
| `departure_time` / `arrival_time` | `TIME NULL` | scheduled |
| `departure_day_offset` / `arrival_day_offset` | `INT NULL` | relative to the service date |
| `transfer_count` | `INT NOT NULL DEFAULT 0` | |
| `additional_minutes` | `INT NULL` | negative = faster; `NULL` = not computable |
| `score` | `BIGINT NOT NULL` | deterministic ranking value; lower is better |
| `recommendation_status` | `VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'` | `AVAILABLE` / `EXPIRED` (produced) |
| `status_known` | `BOOLEAN NOT NULL DEFAULT FALSE` | whether a status row exists for the service date |
| `candidate_status` | `VARCHAR(20) NULL` | known simulated status |
| `candidate_delay_minutes` | `INT NULL` | known delay |
| `explanation` | `VARCHAR(500) NOT NULL` | human-readable |
| `active_key` | `VARCHAR(190) NULL` | derived; `NULL` once `EXPIRED` |
| `created_at` / `updated_at` | `DATETIME(6)` | managed by Hibernate |

Constraints / indexes: `uk_train_recommendations_active_key UNIQUE (active_key)`, the five FKs, and indexes on `journey_id`, `journey_leg_id`, `journey_impact_id`, `disruption_event_id`, `train_id`, `recommendation_status`, `score`, `service_date`, and `(journey_impact_id, recommendation_status)`.

**Why `active_key`**: MySQL permits many `NULL` values in a `UNIQUE` index, so exactly one `AVAILABLE` row can exist per `(journey, leg, impact, candidate train)` while an unlimited, append-only history of previously suggested candidates is kept. When a recommendation stops being feasible it is set to `EXPIRED` and its key is released.

## 5. Configuration and startup

| Property | Env variable | Default | Meaning |
| :--- | :--- | :--- | :--- |
| `trainconcierge.recommendation.min-transfer-buffer-minutes` | `TRAINCONCIERGE_RECOMMENDATION_MIN_TRANSFER_BUFFER_MINUTES` | `15` | Minimum minutes between a replacement's arrival and the next leg's departure |
| `trainconcierge.recommendation.max-results-per-impact` | `TRAINCONCIERGE_RECOMMENDATION_MAX_RESULTS_PER_IMPACT` | `10` | Maximum recommendations kept/returned per impact |
| `trainconcierge.recommendation.max-impacts-per-run` | `TRAINCONCIERGE_RECOMMENDATION_MAX_IMPACTS_PER_RUN` | `50` | Maximum impacts reconciled by one bounded batch run |

The batch run reuses the Phase 5 journey-date window (`trainconcierge.impact.lookback-days` / `forward-days`). A `Clock` bean (system default zone) is provided by `RecommendationConfig` and injected so time-based checks are testable. `trainconcierge.monitoring.*`, `disruption.*` and `impact.*` are unchanged.

```powershell
# 1. MySQL 8.x running, database trainconcierge_db exists (Phase 0)
$env:DB_USERNAME="root"; $env:DB_PASSWORD="P@t!3nc3"

# 2. Start the app - Flyway applies V8 automatically
mvn spring-boot:run

# 3. Run tests / build
mvn test
mvn clean package
```

Expected on startup: `Successfully validated 8 migrations` and `Current version of schema trainconcierge_db: 8`.
The manual trigger works even with `trainconcierge.monitoring.enabled=false`.

## 6. API reference (Postman)

Base URL: `http://localhost:8080/api/v1` · Header: `Content-Type: application/json` (where a body is sent).
**These are unauthenticated development endpoints.**

| # | Method | URL | Purpose |
| :-- | :--- | :--- | :--- |
| 1 | GET | `/journeys/{journeyId}/recommendations?recommendationStatus=&page=&size=&sort=` | Recommendations of one journey (paginated) · `404` if the journey is absent |
| 2 | GET | `/impacts/{impactId}/recommendations?recommendationStatus=&page=&size=&sort=` | Recommendations of one impact (paginated) · `404` if the impact is absent |
| 3 | POST | `/impacts/{impactId}/recommendations/run` | Recompute one impact (no body) · `404` if the impact is absent |
| 4 | POST | `/recommendations/run` | Bounded batch over ongoing impacts (no body) |

Defaults: `recommendationStatus=AVAILABLE` (pass `EXPIRED` for history), `page=0`, `size=20`, `sort=score,asc` then `id,asc`. An unknown `recommendationStatus` → `400`.

### 1. GET `/journeys/{journeyId}/recommendations` · 2. GET `/impacts/{impactId}/recommendations`

`200 OK`:
```json
{
  "content": [
    {
      "id": 1, "journeyId": 1, "journeyReference": "TC-1A2B3C4D",
      "journeyLegId": 1, "legSequence": 1,
      "journeyImpactId": 1, "impactType": "CANCELLATION", "disruptionEventId": 1,
      "replacementTrainId": 2, "replacementTrainNumber": "830002",
      "replacementTrainName": "Alternative Demo", "replacementTrainType": "EXPRESS",
      "serviceDate": "2026-10-28",
      "replacementSourceStation": { "id": 1, "stationCode": "AA1", "name": "Station AA", "city": "Demo" },
      "replacementDestinationStation": { "id": 2, "stationCode": "AB2", "name": "Station AB", "city": "Demo" },
      "replacementDepartureTime": "11:00:00", "replacementDepartureDayOffset": 0,
      "replacementArrivalTime": "13:00:00", "replacementArrivalDayOffset": 0,
      "transferCount": 0, "additionalMinutes": 0, "score": 0,
      "recommendationStatus": "AVAILABLE",
      "liveStatusKnown": true, "liveStatus": "ON_TIME", "liveDelayMinutes": 0,
      "explanation": "Train 830002 (Alternative Demo) can replace leg 1 (AA1 -> AB2) on 2026-10-28: departs 11:00, arrives 13:00; +0 min versus the planned leg; 0 transfer(s); live status for the service date is known (ON_TIME, 0 min delay).",
      "createdAt": "2026-10-26T00:40:11.482+05:30", "updatedAt": "2026-10-26T00:40:11.482+05:30"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
}
```

Missing id → `404`:
```json
{ "timestamp": "2026-10-26T00:40:00+05:30", "status": 404, "error": "Not Found",
  "message": "Journey not found with id: 999999", "path": "/api/v1/journeys/999999/recommendations" }
```

### 3. POST `/impacts/{impactId}/recommendations/run`

`200 OK` (first run, one feasible alternative):
```json
{ "impactsInspected": 1, "candidatesEvaluated": 2, "recommendationsCreated": 1,
  "recommendationsUpdated": 0, "recommendationsExpired": 0, "recommendationsUnchanged": 0,
  "recommendationsReturned": 1, "failures": 0,
  "exclusionReasons": { "ORIGINAL_TRAIN": 1 }, "reason": null }
```

`200 OK` (second run, no data change — idempotent):
```json
{ "impactsInspected": 1, "candidatesEvaluated": 2, "recommendationsCreated": 0,
  "recommendationsUpdated": 0, "recommendationsExpired": 0, "recommendationsUnchanged": 1,
  "recommendationsReturned": 1, "failures": 0,
  "exclusionReasons": { "ORIGINAL_TRAIN": 1 }, "reason": null }
```

`200 OK` (nothing feasible — every candidate was missing timetable data):
```json
{ "impactsInspected": 1, "candidatesEvaluated": 2, "recommendationsCreated": 0,
  "recommendationsUpdated": 0, "recommendationsExpired": 0, "recommendationsUnchanged": 0,
  "recommendationsReturned": 0, "failures": 0,
  "exclusionReasons": { "ORIGINAL_TRAIN": 1, "MISSING_TIMETABLE_DATA": 1 },
  "reason": "INSUFFICIENT_TIMETABLE_DATA" }
```

### 4. POST `/recommendations/run`

`200 OK` (aggregated counters over the bounded batch):
```json
{ "impactsInspected": 3, "candidatesEvaluated": 7, "recommendationsCreated": 4,
  "recommendationsUpdated": 0, "recommendationsExpired": 1, "recommendationsUnchanged": 2,
  "recommendationsReturned": 6, "failures": 0,
  "exclusionReasons": { "ORIGINAL_TRAIN": 2, "CANDIDATE_CANCELLED": 1 },
  "reason": null }
```
`reason` is `null` as soon as anything was returned; `NO_ELIGIBLE_ACTIVE_IMPACT` when no impact was inspected; `ALL_IMPACTS_FAILED` when every inspected impact failed.

## 7. End-to-end Postman workflow

This reuses **only** endpoints implemented in Phases 1–6. The journey date must be inside the analysis window (`[today − 1, today + 7]`), so it uses `today + 2`.

```powershell
$base = "http://localhost:8080/api/v1"
$date = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
```

### Workflow A — cancellation with a feasible alternative

**A1. Master data** — two stations, the disrupted train, and one alternative train serving the same origin → destination.
```powershell
$s1 = (Invoke-RestMethod -Method Post "$base/stations" -ContentType "application/json" `
        -Body (@{ stationCode="P6A"; name="Phase6 Station A"; city="Demo"; state="Demo" } | ConvertTo-Json)).id
$s2 = (Invoke-RestMethod -Method Post "$base/stations" -ContentType "application/json" `
        -Body (@{ stationCode="P6B"; name="Phase6 Station B"; city="Demo"; state="Demo" } | ConvertTo-Json)).id

$broken = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="880101"; name="Phase6 Disrupted"; trainType="EXPRESS";
                  sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id
$alt = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="880102"; name="Phase6 Alternative"; trainType="EXPRESS";
                  sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id

# Timetables (origin has a departure, destination has an arrival)
Invoke-RestMethod -Method Post "$base/trains/$broken/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="08:00:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$broken/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="10:00:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$alt/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="11:00:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$alt/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="13:00:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)
```

**A2. Passenger, journey, cancellation, pipeline**
```powershell
$pax = (Invoke-RestMethod -Method Post "$base/passengers" -ContentType "application/json" `
        -Body (@{ fullName="Phase6 Passenger"; email="phase6a@example.com" } | ConvertTo-Json)).id

$journey = (Invoke-RestMethod -Method Post "$base/journeys" -ContentType "application/json" `
        -Body (@{ passengerId=$pax; journeyDate=$date; legs=@(
                   @{ trainId=$broken; sourceStationId=$s1; destinationStationId=$s2; legSequence=1 }) } `
               | ConvertTo-Json -Depth 5)).id

Invoke-RestMethod -Method Post "$base/trains/$broken/status/cancel" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; reason="Phase6 demo cancellation" } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$alt/status/on-time" -ContentType "application/json" `
  -Body (@{ serviceDate=$date } | ConvertTo-Json)

Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json          # Phase 4: eventsCreated = 1
Invoke-RestMethod -Method Post "$base/impact-analysis/run" | ConvertTo-Json     # Phase 5: impactsCreated = 2
```

**A3. Generate and read recommendations**
```powershell
# find the leg-specific CANCELLATION impact id
$impact = (Invoke-RestMethod "$base/journeys/$journey/impacts").content `
          | Where-Object { $_.impactType -eq "CANCELLATION" } | Select-Object -First 1

Invoke-RestMethod -Method Post "$base/impacts/$($impact.id)/recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsReturned = 1, exclusionReasons.ORIGINAL_TRAIN = 1, reason = null

Invoke-RestMethod "$base/journeys/$journey/recommendations" | ConvertTo-Json -Depth 6
# expect one AVAILABLE recommendation, replacementTrainNumber = 880102, score = 0, liveStatusKnown = true

# journey-level ITINERARY_DISRUPTED carries no leg replacement
$itinerary = (Invoke-RestMethod "$base/journeys/$journey/impacts").content `
             | Where-Object { $_.impactType -eq "ITINERARY_DISRUPTED" } | Select-Object -First 1
Invoke-RestMethod -Method Post "$base/impacts/$($itinerary.id)/recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsReturned = 0, reason = "IMPACT_NOT_LEG_SPECIFIC"
```

### Workflow B — delay that threatens a connection

Two legs: leg 1 (`P6A → P6B`) is delayed, leg 2 departs `P6B` at `14:00`. The alternative reaches `P6B` at `13:00`, i.e. 60 min before the connection (≥ the 15-min buffer) → feasible. A second alternative arriving at `13:50` (only 10 min before) → `CONNECTION_NOT_FEASIBLE`.

```powershell
$s3 = (Invoke-RestMethod -Method Post "$base/stations" -ContentType "application/json" `
        -Body (@{ stationCode="P6C"; name="Phase6 Station C"; city="Demo"; state="Demo" } | ConvertTo-Json)).id

# leg-2 train: departs P6B at 14:00 (source dep) and arrives P6C at 16:00
$leg2 = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="880103"; name="Phase6 Connector"; trainType="EXPRESS";
                  sourceStationId=$s2; destinationStationId=$s3 } | ConvertTo-Json)).id
Invoke-RestMethod -Method Post "$base/trains/$leg2/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=1; arrivalTime=$null; departureTime="14:00:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$leg2/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s3; stopSequence=2; arrivalTime="16:00:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)

# one feasible alternative (arrival 13:00) and one too late (arrival 13:50)
$feasible = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="880104"; name="Phase6 Feasible"; trainType="EXPRESS"; sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id
Invoke-RestMethod -Method Post "$base/trains/$feasible/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="11:00:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$feasible/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="13:00:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)

$tooLate = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="880105"; name="Phase6 TooLate"; trainType="EXPRESS"; sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id
Invoke-RestMethod -Method Post "$base/trains/$tooLate/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="11:10:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$tooLate/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="13:50:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)

# two-leg journey, leg 1 (the disrupted one) on $broken
$pax2 = (Invoke-RestMethod -Method Post "$base/passengers" -ContentType "application/json" `
        -Body (@{ fullName="Phase6 Conn Passenger"; email="phase6b@example.com" } | ConvertTo-Json)).id
$journey2 = (Invoke-RestMethod -Method Post "$base/journeys" -ContentType "application/json" `
        -Body (@{ passengerId=$pax2; journeyDate=$date; legs=@(
                   @{ trainId=$broken;  sourceStationId=$s1; destinationStationId=$s2; legSequence=1 },
                   @{ trainId=$leg2;    sourceStationId=$s2; destinationStationId=$s3; legSequence=2 }) } `
               | ConvertTo-Json -Depth 5)).id

Invoke-RestMethod -Method Post "$base/trains/$broken/status/delay" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; delayMinutes=30; reason="Phase6 demo delay" } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$feasible/status/on-time" -ContentType "application/json" -Body (@{ serviceDate=$date } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
Invoke-RestMethod -Method Post "$base/impact-analysis/run" | ConvertTo-Json

$delayImpact = (Invoke-RestMethod "$base/journeys/$journey2/impacts").content `
               | Where-Object { $_.impactType -eq "DELAY" } | Select-Object -First 1
Invoke-RestMethod -Method Post "$base/impacts/$($delayImpact.id)/recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsReturned = 1 (train 880104), exclusionReasons.CONNECTION_NOT_FEASIBLE = 1
```

### Workflow C — no feasible alternative / missing timetable data

Use a journey whose leg has no other train but the disrupted one, then a second train whose departure time is missing.

```powershell
# C1: only the disrupted train serves the leg -> ALL_CANDIDATES_DISRUPTED
Invoke-RestMethod -Method Post "$base/impacts/$($impact.id)/recommendations/run" | ConvertTo-Json -Depth 5
# (with no alternative train created for the leg) expect reason = "ALL_CANDIDATES_DISRUPTED"

# C2: add a candidate whose origin departure time is missing -> INSUFFICIENT_TIMETABLE_DATA
$broken2 = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="880106"; name="Phase6 MissingDep"; trainType="EXPRESS"; sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id
Invoke-RestMethod -Method Post "$base/trains/$broken2/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime=$null; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$broken2/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="13:00:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)

Invoke-RestMethod -Method Post "$base/impacts/$($impact.id)/recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsReturned = 0, exclusionReasons.MISSING_TIMETABLE_DATA = 1, reason = "INSUFFICIENT_TIMETABLE_DATA"
```

## 8. Verifying ranking determinism and idempotency

- **Determinism**: with three candidates — `ON_TIME`, `DELAYED 40 min`, and one with **no status row** — the returned order is `ON_TIME` (score `0`), then `DELAYED` (score `40`), then the unknown one (score `0 + 10_000_000`), because a known status outranks an unknown one and a lower known delay comes first. Two consecutive runs return the identical order.
- **Tie-breaker**: identical candidates (same score) are ordered by train number, then train id — reproducible across runs and covered by the unit test `documentedTieBreaker`.
- **Idempotency**: `POST /impacts/{id}/recommendations/run` twice with no data change → the second run returns `"recommendationsCreated": 0`, `"recommendationsUnchanged": 1`, `"recommendationsReturned": 1`. `GET` still shows the same row with the same id.
- **Expiry + history**: mark the previously recommended candidate `CANCELLED` for the service date and run again → `"recommendationsExpired": 1`; `GET ...?recommendationStatus=EXPIRED` shows the old row with `recommendationStatus = "EXPIRED"` and `activeKey = null`, and `AVAILABLE` is empty. Nothing is deleted.
- **Result limit**: with `max-results-per-impact=3` and four feasible candidates, exactly three are stored and returned (the three best by score).

## 9. Tests and build results

Commands and honest results (run against the local MySQL 8.0.46 instance, which was available):

```powershell
mvn test -Dtest=RecommendationApiIntegrationTest          # Phase 6 integration only
mvn test -Dtest=RecommendationScoringServiceTest          # Phase 6 scoring unit only
mvn test                                                  # full suite
```

```
Phase 6 integration class:  Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
Phase 6 scoring unit class: Tests run:  8, Failures: 0, Errors: 0, Skipped: 0
Full suite:                 Tests run: 121, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Flyway: Successfully validated 8 migrations - schema version 8
```

`RecommendationApiIntegrationTest` (15 tests) covers: a cancelled train producing a feasible alternative; a journey-level impact returning `IMPACT_NOT_LEG_SPECIFIC`; the original and a cancelled candidate being excluded; known status ranking above unknown and lower delay ranking first; direction and partial coverage being enforced (origin-only and reversed-order trains rejected); connection feasibility against the following leg; a cross-midnight connection; missing timetable data yielding `INSUFFICIENT_TIMETABLE_DATA`; only the disrupted train serving the leg → `ALL_CANDIDATES_DISRUPTED`; an already-departed candidate being excluded; repeated generation being idempotent; a candidate cancellation expiring the recommendation while keeping history; the result limit and pagination; `404` for unknown journey/impact and `400` for a bad status enum; and a Phase 0–5 endpoint regression check.

`RecommendationScoringServiceTest` (8 plain-JUnit tests, no Spring/DB) covers: score stability, full coverage beating partial, lowest additional time first, fewer transfers first, known status beating unknown, lower delay first, the documented tie-breaker, and unknown additional time being penalised.

The scheduled monitor is disabled in the test class; every test rolls back and no external service is contacted.

Known coverage limits: a true concurrent two-writer race is not reproducible deterministically in a JDBC test; the database guarantee it depends on (the `active_key` unique index) *is* exercised by the recomputation tests.

## 10. Troubleshooting

- **`recommendationsCreated` stays `0` while `recommendationsReturned` is `1`**: expected — the recommendation already exists and was confirmed unchanged. Use `POST /impacts/{id}/recommendations/run` after changing candidate data to see `updated`/`expired`.
- **`reason: "INSUFFICIENT_TIMETABLE_DATA"`**: a candidate (or the following leg) is missing a departure/arrival time in `train_stops`. Fix the timetable; times are never invented.
- **`reason: "ALL_CANDIDATES_DISRUPTED"`**: the only train serving the leg is the disrupted one, or every other candidate is cancelled for the service date.
- **A candidate you expected does not appear**: check (a) it is active; (b) its origin stop has a lower `stopSequence` than its destination stop; (c) it has a departure and arrival time; (d) it is not cancelled for the service date; (e) its departure has not already passed (`ALREADY_DEPARTED`); (f) for a multi-leg journey, it makes the connection within `min-transfer-buffer-minutes`.
- **`NO_ELIGIBLE_ACTIVE_IMPACT`**: the impact is not `ACTIVE` — a resolved disruption produces no new suggestions.
- **`400 Bad Request`**: an unknown `recommendationStatus` value (use `AVAILABLE`, `SELECTED`, `EXPIRED`, `UNAVAILABLE`) or a bad pagination/sort parameter.
- **`409 Conflict`**: a `train_recommendations.active_key` unique violation — normally only reachable through concurrent runs; re-run the generation.
- **Flyway failure**: check `flyway_schema_history`; never edit an applied migration — add a new one.
- **Scheduler running during tests**: set `trainconcierge.monitoring.enabled=false` (`TRAINCONCIERGE_MONITORING_ENABLED=false`).
- **MySQL connection errors**: verify `DB_USERNAME`/`DB_PASSWORD`/`DB_NAME` and that MySQL is on `localhost:3306`.
- **`ENOTFOUND` in Postman**: prefix local requests with `http://localhost:8080`.

## 11. Git review and commit

Review first and confirm no secrets (`.env`, `application-local.yml`, credentials) are staged.

```powershell
git status
git diff
git add src/main/resources/db/migration/V8__alternative_train_recommendations.sql `
        src/main/java/com/trainconcierge/entity/RecommendationStatus.java `
        src/main/java/com/trainconcierge/entity/TrainRecommendation.java `
        src/main/java/com/trainconcierge/repository/TrainRecommendationRepository.java `
        src/main/java/com/trainconcierge/repository/TrainStopRepository.java `
        src/main/java/com/trainconcierge/repository/JourneyImpactRepository.java `
        src/main/java/com/trainconcierge/config/RecommendationConfig.java `
        src/main/java/com/trainconcierge/config/TrainConciergeProperties.java `
        src/main/java/com/trainconcierge/service/RecommendationCandidate.java `
        src/main/java/com/trainconcierge/service/RecommendationRunResult.java `
        src/main/java/com/trainconcierge/service/RecommendationScoringService.java `
        src/main/java/com/trainconcierge/service/ImpactRecommendationService.java `
        src/main/java/com/trainconcierge/service/AlternativeRecommendationService.java `
        src/main/java/com/trainconcierge/dto/TrainRecommendationResponse.java `
        src/main/java/com/trainconcierge/dto/RecommendationRunResponse.java `
        src/main/java/com/trainconcierge/controller/RecommendationController.java `
        src/main/resources/application.yml `
        src/test/java/com/trainconcierge/service/RecommendationScoringServiceTest.java `
        src/test/java/com/trainconcierge/recommendation/RecommendationApiIntegrationTest.java `
        phase6.md
git status
git commit -m "Complete TrainConcierge Phase 6 alternative train recommendations"
git push
```

If the branch has no upstream: `git push -u origin HEAD`.

## 12. Recommended commit message

`Complete TrainConcierge Phase 6 alternative train recommendations`
