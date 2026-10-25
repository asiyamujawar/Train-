# TrainConcierge — Phase 5: Journey Impact Analysis (MySQL)

## 1. Objective and implemented features

Link the disruptions detected in **Phase 4** to the journeys planned in **Phase 2**, and record how each journey is affected. Detection is deterministic plain Java — no ML, no external AI API, no live railway API — and reuses the existing entities (`Journey`, `JourneyLeg`, `Train`, `Station`, `TrainStop`, `DisruptionEvent`).

**Implemented**

- `POST /impact-analysis/run` runs one analysis cycle and returns created/updated/resolved/unchanged counters.
- Cancelled leg train → `CANCELLATION` (+ journey-level `ITINERARY_DISRUPTED`).
- Significantly delayed leg train → compare the delayed expected arrival with the next leg's scheduled departure plus the planned transfer buffer:
  - delay beyond the buffer → `MISSED_CONNECTION` (+ `ITINERARY_DISRUPTED`);
  - otherwise → `DELAY`.
- Midnight crossings are handled by treating a departure earlier than the arrival as the next day (one day max — the timetable model has no absolute dates).
- Severity: `CANCELLATION` / `ITINERARY_DISRUPTED` → `CRITICAL`; `MISSED_CONNECTION` by overshoot (`>=60` → `CRITICAL`, `>=30` → `HIGH`, else `MEDIUM`); `DELAY` by delay (`>=120` → `HIGH`, `>=60` → `MEDIUM`, else `LOW`).
- Idempotent and DB-guarded (one ACTIVE impact per journey/leg/event/type); history preserved; impacts are resolved when the disruption is resolved.

**Key design decisions**

| # | Decision | Rationale |
| :-- | :--- | :--- |
| D1 | A leg is only matched to an event for **the same train id and the same service date** (`journey.journeyDate`) | A disruption on another date must not leak into the journey |
| D2 | Only `ACTIVE`/`ACKNOWLEDGED` events are used | Resolved disruptions never create new impacts |
| D3 | A missing status row is **never** treated as on time | Only explicitly ongoing events are read |
| D4 | Missing `arrivalTime`/`departureTime` → no missed connection is claimed; the impact stays `DELAY` with an explicit "cannot be computed" explanation | Missing timetable data is never invented |
| D5 | Impact identity is a derived `active_key` = `J<journeyId>:L<legId\|0>:E<eventId>:<TYPE>`, `NULL` once resolved (leg `0` = journey-level) | A nullable `UNIQUE` column gives *one active impact* and unlimited history (MySQL allows many `NULL`s) |
| D6 | Each journey is reconciled in its own transaction; the run itself is not one big transaction | One failure cannot roll back the others and is reported in `failures` |
| D7 | `detectedAt` is set once; refreshes only update severity/delay/explanation and `lastObservedAt` | The first detection time is historically meaningful |
| D8 | Journeys already carrying an ACTIVE impact are always revisited | This is how a since-resolved disruption closes its impacts |
| D9 | The analysis is decoupled from the Phase 4 scheduler (calls it never) | Manual runs work with `monitoring.enabled=false` |
| D10 | Endpoints are **unauthenticated development endpoints** | No authentication is implemented yet |

## 2. Files created / modified

**Created**

- [V7__journey_impact_analysis.sql](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/db/migration/V7__journey_impact_analysis.sql)
- [ImpactType.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/ImpactType.java) · [ImpactSeverity.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/ImpactSeverity.java) · [ImpactState.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/ImpactState.java) · [JourneyImpact.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/JourneyImpact.java)
- [JourneyImpactRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/JourneyImpactRepository.java)
- [JourneyImpactService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/JourneyImpactService.java) · [ImpactAnalysisService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/ImpactAnalysisService.java) · [DesiredImpact.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/DesiredImpact.java) · [JourneyImpactOutcome.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/JourneyImpactOutcome.java) · [ImpactAnalysisResult.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/ImpactAnalysisResult.java)
- [JourneyImpactResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/JourneyImpactResponse.java) · [ImpactAnalysisRunResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/ImpactAnalysisRunResponse.java)
- [ImpactAnalysisController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/ImpactAnalysisController.java) · [JourneyImpactController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/JourneyImpactController.java)
- [JourneyImpactAnalysisApiIntegrationTest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/test/java/com/trainconcierge/impact/JourneyImpactAnalysisApiIntegrationTest.java)
- `phase5.md` (this file)

**Modified (additive only)**

- [TrainConciergeProperties.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/TrainConciergeProperties.java) — added the `impact` block.
- [JourneyRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/JourneyRepository.java) — added `findJourneysInDateRangeWithLegs`.
- [JourneyLegRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/JourneyLegRepository.java) — added `findLegsWithDetails`.
- [DisruptionEventRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/DisruptionEventRepository.java) — added `findEventsByStateWithTrain`.
- [application.yml](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/application.yml) — added the `trainconcierge.impact` block.

No Phase 0–4 behaviour was changed and no applied migration was edited.

## 3. Flyway migration

New migration: `V7__journey_impact_analysis.sql` (`V1`–`V6` untouched). Flyway applies it automatically at startup; there is nothing to run by hand.

Table `journey_impacts`:

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | `BIGINT AUTO_INCREMENT` | PK |
| `journey_id` | `BIGINT NOT NULL` | FK → `journeys(id)` `ON DELETE CASCADE` |
| `journey_leg_id` | `BIGINT NULL` | FK → `journey_legs(id)`; `NULL` for journey-level impacts |
| `disruption_event_id` | `BIGINT NOT NULL` | FK → `disruption_events(id)` `ON DELETE CASCADE` |
| `impact_type` | `VARCHAR(30) NOT NULL` | `DELAY`, `CANCELLATION`, `MISSED_CONNECTION`, `ITINERARY_DISRUPTED` |
| `severity` | `VARCHAR(20) NOT NULL` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `delay_minutes` | `INT NULL` | known only for delay-driven impacts |
| `explanation` | `VARCHAR(500) NOT NULL` | human-readable |
| `detected_at` | `DATETIME(6) NOT NULL` | set once |
| `last_observed_at` | `DATETIME(6) NOT NULL` | refreshed each run |
| `impact_state` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | `ACTIVE` / `RESOLVED` |
| `resolved_at` | `DATETIME(6) NULL` | |
| `active_key` | `VARCHAR(160) NULL` | derived; `NULL` once resolved |
| `created_at` / `updated_at` | `DATETIME(6)` | project convention |

Constraints / indexes: `uk_journey_impacts_active_key UNIQUE (active_key)`, the three FKs, and indexes on `journey_id`, `journey_leg_id`, `disruption_event_id`, `impact_type`, `severity`, `impact_state`, `detected_at`, `(journey_id, impact_state)`.

## 4. Configuration and startup

| Property | Env variable | Default | Meaning |
| :--- | :--- | :--- | :--- |
| `trainconcierge.impact.lookback-days` | `TRAINCONCIERGE_IMPACT_LOOKBACK_DAYS` | `1` | Window start = `today − lookback-days` |
| `trainconcierge.impact.forward-days` | `TRAINCONCIERGE_IMPACT_FORWARD_DAYS` | `7` | Window end = `today + forward-days` |

Only journeys whose `journey_date` is inside that window are analysed. `trainconcierge.monitoring.*` and `trainconcierge.disruption.*` are unchanged from Phase 4.

```powershell
# 1. MySQL 8.x running, database trainconcierge_db exists (Phase 0)
$env:DB_USERNAME="root"; $env:DB_PASSWORD="P@t!3nc3"

# 2. Start the app - Flyway applies V7 automatically
mvn spring-boot:run

# 3. Run tests / build
mvn test
mvn clean package
```

Expected on startup: `Successfully validated 7 migrations` and `Current version of schema trainconcierge_db: 7`.
The manual trigger works even with `trainconcierge.monitoring.enabled=false`.

## 5. API reference (Postman)

Base URL: `http://localhost:8080/api/v1` · Header: `Content-Type: application/json` (where a body is sent).
**These are unauthenticated development endpoints.**

| # | Method | URL | Purpose |
| :-- | :--- | :--- | :--- |
| 1 | POST | `/impact-analysis/run` | Run one analysis cycle (no body) |
| 2 | GET | `/impacts?journeyId=&disruptionEventId=&impactType=&severity=&impactState=&minDelayMinutes=&page=&size=&sort=detectedAt,desc` | Paginated list, all filters optional |
| 3 | GET | `/impacts/{id}` | One impact · `404` if absent |
| 4 | GET | `/journeys/{journeyId}/impacts?page=&size=` | Impacts of one journey · `404` if the journey is absent |

### 1. POST `/impact-analysis/run`

`200 OK`:
```json
{ "journeysInspected": 2, "eventsInspected": 1, "impactsCreated": 2,
  "impactsUpdated": 0, "impactsResolved": 0, "impactsUnchanged": 0, "failures": 0 }
```
The second call returns `"impactsCreated": 0` and `"impactsUnchanged": 2`.

### 2. GET `/impacts`

`200 OK`:
```json
{
  "content": [
    {
      "id": 1, "journeyId": 1, "journeyReference": "TC-1A2B3C4D",
      "journeyLegId": 1, "legSequence": 1, "legTrainNumber": "12951",
      "disruptionEventId": 1, "disruptionType": "SIGNIFICANT_DELAY", "serviceDate": "2026-10-28",
      "impactType": "DELAY", "severity": "LOW", "delayMinutes": 45,
      "explanation": "Leg 1 (train 12951) is delayed 45 min; this is the final leg, so no connection is at risk.",
      "detectedAt": "2026-10-26T00:20:11.482+05:30", "lastObservedAt": "2026-10-26T00:20:11.482+05:30",
      "impactState": "ACTIVE", "resolvedAt": null,
      "createdAt": "2026-10-26T00:20:11.482+05:30", "updatedAt": "2026-10-26T00:20:11.482+05:30"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
}
```
`minDelayMinutes=-1` → `400`. An unknown `impactType`/`severity`/`impactState` → `400`.

### 3. GET `/impacts/{id}` · 4. GET `/journeys/{journeyId}/impacts`

`200 OK` (single object / pagination envelope). Missing id → `404`:
```json
{ "timestamp": "2026-10-26T00:20:00+05:30", "status": 404, "error": "Not Found",
  "message": "Journey impact not found with id: 999999", "path": "/api/v1/impacts/999999" }
```

## 6. End-to-end Postman workflow

This reuses **only** endpoints implemented in Phases 1–5. The journey date must be inside the analysis window (`[today − 1, today + 7]`), so it uses `today + 2`.

```powershell
$base = "http://localhost:8080/api/v1"
$date = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
```

**A. Master data** — create two stations and one train, then its timetable. Use the real ids returned.
```powershell
$s1 = (Invoke-RestMethod -Method Post "$base/stations" -ContentType "application/json" `
        -Body (@{ stationCode="P5A"; name="Phase5 Station A"; city="Demo"; state="Demo" } | ConvertTo-Json)).id
$s2 = (Invoke-RestMethod -Method Post "$base/stations" -ContentType "application/json" `
        -Body (@{ stationCode="P5B"; name="Phase5 Station B"; city="Demo"; state="Demo" } | ConvertTo-Json)).id
$train = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="880001"; name="Phase5 Demo Train"; trainType="EXPRESS";
                  sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id

Invoke-RestMethod -Method Post "$base/trains/$train/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="08:00:00";
            dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$train/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="10:00:00"; departureTime=$null;
            dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)
```

**B. Passenger + journey** (journey legs get their times from the timetable)
```powershell
$pax = (Invoke-RestMethod -Method Post "$base/passengers" -ContentType "application/json" `
        -Body (@{ fullName="Phase5 Passenger"; email="phase5@example.com" } | ConvertTo-Json)).id

$journey = (Invoke-RestMethod -Method Post "$base/journeys" -ContentType "application/json" `
        -Body (@{ passengerId=$pax; journeyDate=$date; legs=@(
                   @{ trainId=$train; sourceStationId=$s1; destinationStationId=$s2; legSequence=1 }) } `
               | ConvertTo-Json -Depth 5)).id
```

**C. Simulate a delay, monitor, analyse, verify**
```powershell
Invoke-RestMethod -Method Post "$base/trains/$train/status/delay" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; delayMinutes=45; reason="Phase5 demo delay" } | ConvertTo-Json)

Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json          # Phase 4: eventsCreated = 1
Invoke-RestMethod -Method Post "$base/impact-analysis/run" | ConvertTo-Json     # Phase 5: impactsCreated = 1

Invoke-RestMethod "$base/journeys/$journey/impacts" | ConvertTo-Json -Depth 6
# expect one DELAY impact, delayMinutes = 45, severity LOW, impactState ACTIVE
```

**D. Simulate a cancellation** (same journey; a cancellation replaces the delay event)
```powershell
Invoke-RestMethod -Method Post "$base/trains/$train/status/cancel" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; reason="Phase5 demo cancellation" } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
Invoke-RestMethod -Method Post "$base/impact-analysis/run" | ConvertTo-Json
# expect impactsCreated = 2  (CANCELLATION + ITINERARY_DISRUPTED), impactsResolved = 1 (the old DELAY)

Invoke-RestMethod "$base/journeys/$journey/impacts" | ConvertTo-Json -Depth 6
# expect CANCELLATION (CRITICAL, journeyLegId = leg 1) and ITINERARY_DISRUPTED (CRITICAL, journeyLegId = null)
```

## 7. Verifying missed-connection logic (documented example)

Two legs, all times taken from real `train_stops` rows:

| Leg | Train | From → To | Scheduled |
| :-- | :--- | :--- | :--- |
| 1 | A | `P5A → P5B` | depart `08:00`, arrive `10:00` |
| 2 | B | `P5B → P5C` | depart `10:30`, arrive `12:00` |

Planned transfer buffer at `P5B` = `10:30 − 10:00` = **30 min**.
A **45-minute** delay on train A gives expected arrival `10:45` → `45 > 30` → overshoot `15` → **`MISSED_CONNECTION` / `MEDIUM`**, plus a journey-level `ITINERARY_DISRUPTED`.

Set leg 2's departure to `12:00` instead (`buffer = 120`) and the same 45-minute delay produces a plain **`DELAY` / `LOW`** — the connection is not threatened.

To reproduce with the API: create a third station `P5C`, a second train `880002` with stops `P5B(stopSequence=1, departureTime=10:30)` and `P5C(stopSequence=2, arrivalTime=12:00)`, then create a two-leg journey (`legSequence 1` = train A `P5A→P5B`, `legSequence 2` = train B `P5B→P5C`). Delay train A by 45 min, then `POST /monitoring/run` and `POST /impact-analysis/run`.

*Midnight variant* (also covered by a test): arrival `23:30`, next departure `00:30` → the departure is treated as the next day, buffer = `(00:30 + 24h) − 23:30` = 60 min.

## 8. Verifying duplicate prevention and lifecycle

- **Duplicates**: run `POST /impact-analysis/run` twice with no data change → the second run returns `"impactsCreated": 0` and `"impactsUnchanged": 2`. `GET /journeys/{id}/impacts` still shows the same two rows with the same ids and `detectedAt`.
- **Lifecycle**: `POST /trains/$train/status/restore` (body `{ "serviceDate": "$date" }`) → `POST /monitoring/run` (event becomes `RESOLVED`) → `POST /impact-analysis/run` → the run reports `impactsResolved >= 2`; `GET /journeys/{id}/impacts` shows those rows with `"impactState": "RESOLVED"` and a filled `resolvedAt`. Nothing is deleted, and a later re-disruption creates **new** rows.

## 9. Tests and build results

Commands and honest results (run against the local MySQL 8.0.46 instance, which was available):

```powershell
mvn test -Dtest=JourneyImpactAnalysisApiIntegrationTest   # Phase 5 only
mvn test                                                  # full suite
```

```
Phase 5 class:  Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
Full suite:     Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Flyway: Successfully validated 7 migrations - schema version 7
```

`JourneyImpactAnalysisApiIntegrationTest` (14 tests) covers: cancelled train; delayed final leg; a delay that threatens the next connection; a delay that does not; a midnight-crossing connection; an unrelated journey staying unaffected; missing timetable data (no missed connection claimed); a disruption on a different service date; a resolved disruption not creating new active impacts and reconciling existing ones; repeated analysis not duplicating; filtering/pagination/`400`; `404` for missing impact and journey; DB-level `active_key` uniqueness; and a Phase 0–4 endpoint regression check. The scheduled monitor is disabled in the test class.

Known coverage limits: a true concurrent two-writer race is not reproducible deterministically in a JDBC test; the database guarantee it depends on *is* tested directly (a duplicate `active_key` insert is expected to raise `DataIntegrityViolationException`).

## 10. Troubleshooting

- **`impactsCreated` stays `0`**: the journey's `journey_date` must equal the disruption's `service_date` and be inside `[today − 1, today + 7]`; also make sure Phase 4 actually created an event (`GET /disruptions?...&eventState=ACTIVE`).
- **A delay produces `DELAY` instead of `MISSED_CONNECTION`**: the delay must exceed the transfer buffer, and both the arrival and departure times must exist. Missing times intentionally yield `DELAY` with a "cannot be computed" explanation.
- **`409 Conflict`**: a `journey_impacts.active_key` unique violation — normally only reachable through concurrent runs; re-run the analysis.
- **Flyway failure**: check `flyway_schema_history`; never edit an applied migration — add a new one.
- **Scheduler running during tests**: set `trainconcierge.monitoring.enabled=false` (`TRAINCONCIERGE_MONITORING_ENABLED=false`).
- **MySQL connection errors**: verify `DB_USERNAME`/`DB_PASSWORD`/`DB_NAME` and that MySQL is on `localhost:3306`.
- **`ENOTFOUND` in Postman**: prefix local requests with `http://localhost:8080`.

## 11. Git review and commit

Review first and confirm no secrets (`.env`, `application-local.yml`, credentials) are staged.

```powershell
git status
git diff
git add src/main/resources/db/migration/V7__journey_impact_analysis.sql `
        src/main/java/com/trainconcierge/entity/ImpactType.java `
        src/main/java/com/trainconcierge/entity/ImpactSeverity.java `
        src/main/java/com/trainconcierge/entity/ImpactState.java `
        src/main/java/com/trainconcierge/entity/JourneyImpact.java `
        src/main/java/com/trainconcierge/repository/JourneyImpactRepository.java `
        src/main/java/com/trainconcierge/repository/JourneyRepository.java `
        src/main/java/com/trainconcierge/repository/JourneyLegRepository.java `
        src/main/java/com/trainconcierge/repository/DisruptionEventRepository.java `
        src/main/java/com/trainconcierge/config/TrainConciergeProperties.java `
        src/main/java/com/trainconcierge/service/JourneyImpactService.java `
        src/main/java/com/trainconcierge/service/ImpactAnalysisService.java `
        src/main/java/com/trainconcierge/service/DesiredImpact.java `
        src/main/java/com/trainconcierge/service/JourneyImpactOutcome.java `
        src/main/java/com/trainconcierge/service/ImpactAnalysisResult.java `
        src/main/java/com/trainconcierge/dto/JourneyImpactResponse.java `
        src/main/java/com/trainconcierge/dto/ImpactAnalysisRunResponse.java `
        src/main/java/com/trainconcierge/controller/ImpactAnalysisController.java `
        src/main/java/com/trainconcierge/controller/JourneyImpactController.java `
        src/main/resources/application.yml `
        src/test/java/com/trainconcierge/impact/JourneyImpactAnalysisApiIntegrationTest.java `
        phase5.md
git status
git commit -m "Complete TrainConcierge Phase 5 journey impact analysis"
git push
```

If the branch has no upstream: `git push -u origin HEAD`.

**Recommended commit message**: `Complete TrainConcierge Phase 5 journey impact analysis`
