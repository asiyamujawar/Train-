# TrainConcierge — Phase 4: Automated Disruption Monitoring (MySQL)

## 1. Features and design decisions

Automatically detects significant delays and cancellations from the **simulated** running statuses created in Phase 3, and persists disruption events that Phase 5 can use to identify affected passengers. No live railway APIs and no AI/ML are used.

**Implemented**

- Scheduled monitoring (fixed delay, default 60s) plus a manual trigger endpoint.
- Delay detection (`DELAYED` at or above a configurable threshold) and cancellation detection (`CANCELLED`).
- `DisruptionEvent` lifecycle: `ACTIVE` → `ACKNOWLEDGED` → `RESOLVED`.
- Idempotent monitoring: repeated runs never duplicate an ongoing event.
- Automatic resolution when a train returns to `ON_TIME`, and automatic type switching between delay and cancellation.
- Historical events are never deleted.
- Fully configurable through properties **and** environment variables.

**Design decisions (and why)**

| # | Decision | Rationale |
| :-- | :--- | :--- |
| D1 | Detection rules: `DELAYED` and `delayMinutes >= threshold` → `SIGNIFICANT_DELAY`; `CANCELLED` → `CANCELLATION`; anything else → no active disruption | A delay below the threshold is not a disruption |
| D2 | Missing status data is **never** treated as on time | Only rows that actually exist are inspected |
| D3 | A cancellation always stores `delayMinutes = 0` | A cancellation is not a delay (consistent with Phase 3) |
| D4 | Event identity is a derived `active_key` = `T<trainId>:<serviceDate>:<disruptionType>`, populated only while the event is ongoing and set to `NULL` when resolved | A `UNIQUE` index on a nullable column gives *one ongoing event per train/date/type* **and** unlimited historical recurrences (MySQL allows many `NULL`s) |
| D5 | Duplicate prevention is enforced both by an application lookup **and** by the database unique constraint | An application-only check cannot survive concurrent runs |
| D6 | Each train/service-date is applied in its own transaction; the run is **not** one giant transaction | One failure cannot roll back everything else, and it can be reported as a failure count |
| D7 | A concurrent duplicate insert (`DataIntegrityViolationException`) falls back to refreshing the already-committed event | The losing writer still records the observation instead of failing the run |
| D8 | An ongoing event refreshed by repeat observations updates `lastObservedAt` but keeps its original `detectedAt` | The first detection time is historically meaningful |
| D9 | A **recurrence** after resolution creates a **new** row (it never reuses or re-activates the resolved one) | Keeps a complete, immutable history of distinct disruption episodes |
| D10 | `ACKNOWLEDGED` still counts as ongoing and keeps its `active_key` | Acknowledging is not resolving |
| D11 | Monitoring is limited to a service-date window `[today − lookbackDays, today + forwardDays]` | Bounded, predictable work; documented below |
| D12 | Timestamps are server-generated `OffsetDateTime` (JVM/DB session zone, `Asia/Calcutta` locally) — the same convention as Phase 3 | Clients can never supply times |
| D13 | The scheduler is a thin `@Component` that only delegates; an overlap guard (`ReentrantLock.tryLock`) plus fixed-delay scheduling prevents overlapping runs in one instance | Keeps scheduling and business logic separate |
| D14 | The scheduler bean is created only when `trainconcierge.monitoring.enabled=true` | Easy to disable in tests and local debugging |
| D15 | `POST /monitoring/run`, `PUT`/`POST` status and acknowledgement endpoints are **development endpoints and are NOT secured** — no authentication is implemented yet | Stated explicitly so it is not mistaken for production-ready |

## 2. Files created / modified

**Created**

- [V6__disruption_monitoring.sql](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/db/migration/V6__disruption_monitoring.sql)
- [DisruptionType.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/DisruptionType.java)
- [EventState.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/EventState.java)
- [DisruptionEvent.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/DisruptionEvent.java)
- [DisruptionEventRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/DisruptionEventRepository.java)
- [TrainConciergeProperties.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/TrainConciergeProperties.java)
- [DisruptionMonitoringConfig.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/DisruptionMonitoringConfig.java)
- [DisruptionEventService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/DisruptionEventService.java)
- [DisruptionMonitoringService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/DisruptionMonitoringService.java)
- [ObservationResult.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/ObservationResult.java)
- [MonitoringRunResult.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/MonitoringRunResult.java)
- [DisruptionMonitoringScheduler.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/scheduler/DisruptionMonitoringScheduler.java)
- [DisruptionEventResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/DisruptionEventResponse.java)
- [MonitoringRunResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/MonitoringRunResponse.java)
- [DisruptionController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/DisruptionController.java)
- [MonitoringController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/MonitoringController.java)
- [DisruptionMonitoringApiIntegrationTest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/test/java/com/trainconcierge/disruption/DisruptionMonitoringApiIntegrationTest.java)
- `phase4.md` (this file)

**Modified (additive only)**

- [TrainStatusRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/TrainStatusRepository.java) — added `findStatusesInServiceDateRange` for the monitor.
- [application.yml](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/application.yml) — added the `trainconcierge.*` Phase 4 block with environment-variable overrides.

No Phase 0–3 behaviour was changed and no existing migration was edited.

## 3. Flyway migration and MySQL changes

New migration: `V6__disruption_monitoring.sql` (latest of 6; `V1`–`V5` untouched).

Table `disruption_events`:

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | `BIGINT AUTO_INCREMENT` | PK |
| `train_id` | `BIGINT NOT NULL` | FK → `trains(id)` `ON DELETE CASCADE` |
| `service_date` | `DATE NOT NULL` | |
| `disruption_type` | `VARCHAR(30) NOT NULL` | `SIGNIFICANT_DELAY`, `CANCELLATION` |
| `observed_train_status` | `VARCHAR(20) NOT NULL` | `DELAYED`, `CANCELLED` |
| `delay_minutes` | `INT NOT NULL DEFAULT 0` | |
| `reason` | `VARCHAR(255) NULL` | copied from the status |
| `detected_at` | `DATETIME(6) NOT NULL` | set once |
| `last_observed_at` | `DATETIME(6) NOT NULL` | refreshed on every observation |
| `event_state` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | |
| `resolved_at` | `DATETIME(6) NULL` | |
| `active_key` | `VARCHAR(120) NULL` | derived; `NULL` once resolved |
| `created_at` / `updated_at` | `DATETIME(6)` | existing project convention |

Constraints / indexes: `uk_disruption_events_active_key UNIQUE (active_key)`, FK to `trains`, and indexes on `(train_id, service_date)`, `disruption_type`, `event_state`, `detected_at`, `delay_minutes`.

## 4. Configuration

| Property | Environment variable | Default | Meaning |
| :--- | :--- | :--- | :--- |
| `trainconcierge.monitoring.fixed-delay-ms` | `TRAINCONCIERGE_MONITORING_FIXED_DELAY_MS` | `60000` | Milliseconds between scheduled runs (fixed delay) |
| `trainconcierge.monitoring.enabled` | `TRAINCONCIERGE_MONITORING_ENABLED` | `true` | Master switch for the scheduled monitor |
| `trainconcierge.disruption.delay-threshold-minutes` | `TRAINCONCIERGE_DISRUPTION_DELAY_THRESHOLD_MINUTES` | `30` | A delay at or above this is a `SIGNIFICANT_DELAY` |
| `trainconcierge.disruption.lookback-days` | `TRAINCONCIERGE_DISRUPTION_LOOKBACK_DAYS` | `1` | Window start = `today − lookback-days` |
| `trainconcierge.disruption.forward-days` | `TRAINCONCIERGE_DISRUPTION_FORWARD_DAYS` | `7` | Window end = `today + forward-days` |

Only service dates inside that window are monitored. Example override:

```powershell
$env:TRAINCONCIERGE_DISRUPTION_DELAY_THRESHOLD_MINUTES="20"
$env:TRAINCONCIERGE_MONITORING_FIXED_DELAY_MS="30000"
```

## 5. Disabling the scheduler during tests / local debugging

Either property works:

```powershell
# a) property
mvn test -Dspring-boot.run.arguments="--trainconcierge.monitoring.enabled=false"

# b) environment variable (used by the Phase 4 test class)
$env:TRAINCONCIERGE_MONITORING_ENABLED="false"
```

The Phase 4 integration test disables it declaratively:

```java
@SpringBootTest(properties = {
        "trainconcierge.monitoring.enabled=false",
        "trainconcierge.disruption.delay-threshold-minutes=30"
})
```

The manual endpoint `POST /api/v1/monitoring/run` always works, whether or not the scheduler is enabled.

## 6. Startup instructions

```powershell
# 1. MySQL 8.x running; database trainconcierge_db exists (created in Phase 0)
$env:DB_USERNAME="root"
$env:DB_PASSWORD="P@t!3nc3"

# 2. Start the app - Flyway applies V6 automatically
mvn spring-boot:run

# 3. Run the test suite
mvn test

# 4. Build a runnable jar
mvn clean package
java -jar target/trainconcierge-0.0.1-SNAPSHOT.jar
```

Expected on startup: `Successfully validated 6 migrations` and `Current version of schema trainconcierge_db: 6`.

## 7. API reference (Postman)

Base URL: `http://localhost:8080/api/v1` · Header: `Content-Type: application/json`
**These endpoints are unauthenticated development endpoints.**

| # | Method | URL | Purpose |
| :-- | :--- | :--- | :--- |
| 1 | GET | `/disruptions?trainId=&serviceDate=&disruptionType=&eventState=&minDelayMinutes=&page=&size=&sort=detectedAt,desc` | Paginated list, all filters optional |
| 2 | GET | `/disruptions/{id}` | One event · `404` if absent |
| 3 | GET | `/trains/{trainId}/disruptions?serviceDate=&eventState=` | Paginated events for a train |
| 4 | POST | `/monitoring/run` | Run one cycle now (no body) |
| 5 | POST | `/disruptions/{id}/acknowledge` | Acknowledge without resolving (no body) |

### 1. GET `/disruptions`

`200 OK`:
```json
{
  "content": [
    {
      "id": 1, "trainId": 1, "trainNumber": "12951", "trainName": "Mumbai Rajdhani Express (demo)",
      "serviceDate": "2026-10-27", "disruptionType": "SIGNIFICANT_DELAY",
      "observedTrainStatus": "DELAYED", "delayMinutes": 90,
      "reason": "Demonstration delay caused by operational disruption",
      "detectedAt": "2026-10-26T00:20:11.482+05:30", "lastObservedAt": "2026-10-26T00:20:11.482+05:30",
      "eventState": "ACTIVE", "resolvedAt": null,
      "createdAt": "2026-10-26T00:20:11.482+05:30", "updatedAt": "2026-10-26T00:20:11.482+05:30"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
}
```
`minDelayMinutes=-1` → `400 Bad Request`.

### 2. GET `/disruptions/{id}`

`200 OK` with a single event object, or:
```json
{ "timestamp": "2026-10-26T00:20:00+05:30", "status": 404, "error": "Not Found",
  "message": "Disruption event not found with id: 999999", "path": "/api/v1/disruptions/999999" }
```

### 3. GET `/trains/{trainId}/disruptions`

`200 OK` with the same pagination envelope.

### 4. POST `/monitoring/run`

No body. `200 OK`:
```json
{ "statusesInspected": 3, "eventsCreated": 1, "eventsUpdated": 0, "eventsResolved": 0, "failures": 0 }
```
Repeat immediately → `eventsCreated` is `0` and `eventsUpdated` reflects the ongoing event.

### 5. POST `/disruptions/{id}/acknowledge`

`200 OK` with `"eventState": "ACKNOWLEDGED"` and `"resolvedAt": null`.
Already acknowledged → `200 OK`, unchanged. Already resolved → `400 Bad Request`:
```json
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "message": "A resolved disruption event cannot be acknowledged", "path": "/api/v1/disruptions/1/acknowledge" }
```

## 8. Complete demonstration workflow

The service date must be inside the monitoring window (default `[today − 1, today + 7]`), so this uses tomorrow.

```powershell
$base = "http://localhost:8080/api/v1"
$date = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$trainId = 1   # take a real id from GET $base/trains
```

### A. 90-minute delay

**A1. Create a 90-minute delay with the Phase 3 API**
```powershell
Invoke-RestMethod -Method Put "$base/trains/$trainId/status" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; status="DELAYED"; delayMinutes=90; reason="Demonstration delay caused by operational disruption" } | ConvertTo-Json)
```

**A2. Trigger monitoring**
```powershell
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
# expect: statusesInspected >= 1, eventsCreated = 1
```

**A3. Retrieve the event**
```powershell
Invoke-RestMethod "$base/disruptions?trainId=$trainId&serviceDate=$date" | ConvertTo-Json -Depth 5
# expect: disruptionType = SIGNIFICANT_DELAY, delayMinutes = 90, eventState = ACTIVE
$eventId = (Invoke-RestMethod "$base/disruptions?trainId=$trainId&serviceDate=$date").content[0].id
```

**A4. Re-run monitoring to prove there is no duplicate**
```powershell
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
# expect: eventsCreated = 0, eventsUpdated = 1
Invoke-RestMethod "$base/disruptions?trainId=$trainId&serviceDate=$date" | ConvertTo-Json -Depth 5
# expect: still exactly one event, same id and same detectedAt
```

**A5. Restore the train to on time, then re-run monitoring**
```powershell
Invoke-RestMethod -Method Put "$base/trains/$trainId/status" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; status="ON_TIME"; delayMinutes=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
# expect: eventsResolved = 1
Invoke-RestMethod "$base/disruptions/$eventId" | ConvertTo-Json
# expect: eventState = RESOLVED, resolvedAt filled in
```

### B. Cancellation

**B1. Cancel the train (Phase 3 API)**
```powershell
Invoke-RestMethod -Method Post "$base/trains/$trainId/status/cancel" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; reason="Demonstration cancellation" } | ConvertTo-Json)
```

**B2. Trigger monitoring and retrieve the event**
```powershell
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
# expect: eventsCreated = 1
Invoke-RestMethod "$base/disruptions?trainId=$trainId&serviceDate=$date&eventState=ACTIVE" | ConvertTo-Json -Depth 5
# expect: disruptionType = CANCELLATION, delayMinutes = 0
```

**B3. Re-run monitoring to prove idempotency**
```powershell
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
# expect: eventsCreated = 0
```

**B4. Restore and verify resolution**
```powershell
Invoke-RestMethod -Method Post "$base/trains/$trainId/status/restore" -ContentType "application/json" `
  -Body (@{ serviceDate=$date } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
# expect: eventsResolved = 1
Invoke-RestMethod "$base/trains/$trainId/disruptions?serviceDate=$date" | ConvertTo-Json -Depth 5
# expect: the delay event (from A) and the cancellation event, both RESOLVED
```

Optional: `POST $base/disruptions/{id}/acknowledge` while an event is `ACTIVE` to move it to `ACKNOWLEDGED` without resolving it.

## 9. Test results

Actual `mvn test` run against MySQL 8.0.46 (MySQL was available):

```
Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Flyway: Successfully validated 6 migrations - schema version 6
```

- **New: `DisruptionMonitoringApiIntegrationTest` — 16 tests**, covering: delay at threshold, delay below threshold, cancellation, on-time, missing status data, duplicate prevention across repeated runs, refreshing an ongoing event (`detectedAt` unchanged), resolution after restoration, delay→cancellation, cancellation→delay, recurrence after resolution, filtering/pagination/`404`, acknowledgement (including rejection after resolution), DB-level `active_key` uniqueness, and idempotency of `POST /monitoring/run`.
- **Regression: Phases 1–3 — 68 tests still pass** (`StationApiIntegrationTest`, `TrainTimetableApiIntegrationTest`, `PassengerApiIntegrationTest`, `JourneyApiIntegrationTest`, `TrainStatusApiIntegrationTest`, `StationControllerWebMvcTest`, `SystemControllerWebMvcTest`, `EchoRequestValidationTest`, `TrainConciergeApplicationTests`).

Notes on coverage limits:

- The concurrent-insert fallback in `DisruptionMonitoringService` is exercised indirectly; a true two-thread race is not reproducible deterministically in a JDBC test. The database guarantee it relies on **is** tested directly (test 15 inserts two rows with the same `active_key` and expects a `DataIntegrityViolationException`).
- No test was skipped for lack of MySQL; MySQL 8.0.46 was available for the whole run.

## 10. Troubleshooting

- **No event appears after triggering monitoring**: the status's `service_date` must be inside `[today − lookback-days, today + forward-days]`; widen `TRAINCONCIERGE_DISRUPTION_FORWARD_DAYS` or use a date in range. Also confirm the delay is **at or above** the threshold.
- **Scheduler runs during tests**: set `trainconcierge.monitoring.enabled=false` (or `TRAINCONCIERGE_MONITORING_ENABLED=false`).
- **`409 Conflict` on a status write**: the Phase 3 unique key `(train_id, service_date)` was violated; use `PUT` to update instead of inserting twice.
- **Unexpected duplicate events**: check `active_key` — it must be `NULL` for any `RESOLVED` row. If a resolved row still holds a key, resolution was bypassed.
- **Migration error**: check `flyway_schema_history`; never edit an applied migration.
- **`ENOTFOUND` in Postman**: prefix local requests with `http://localhost:8080`.

## 11. Git review and push

Review everything first, and confirm no secrets (`.env`, `application-local.yml`, credentials) are staged.

```powershell
git status
git diff
git add src/main/resources/db/migration/V6__disruption_monitoring.sql `
        src/main/java/com/trainconcierge/entity/DisruptionType.java `
        src/main/java/com/trainconcierge/entity/EventState.java `
        src/main/java/com/trainconcierge/entity/DisruptionEvent.java `
        src/main/java/com/trainconcierge/repository/DisruptionEventRepository.java `
        src/main/java/com/trainconcierge/repository/TrainStatusRepository.java `
        src/main/java/com/trainconcierge/config/TrainConciergeProperties.java `
        src/main/java/com/trainconcierge/config/DisruptionMonitoringConfig.java `
        src/main/java/com/trainconcierge/service/DisruptionEventService.java `
        src/main/java/com/trainconcierge/service/DisruptionMonitoringService.java `
        src/main/java/com/trainconcierge/service/ObservationResult.java `
        src/main/java/com/trainconcierge/service/MonitoringRunResult.java `
        src/main/java/com/trainconcierge/scheduler/DisruptionMonitoringScheduler.java `
        src/main/java/com/trainconcierge/dto/DisruptionEventResponse.java `
        src/main/java/com/trainconcierge/dto/MonitoringRunResponse.java `
        src/main/java/com/trainconcierge/controller/DisruptionController.java `
        src/main/java/com/trainconcierge/controller/MonitoringController.java `
        src/main/resources/application.yml `
        src/test/java/com/trainconcierge/disruption/DisruptionMonitoringApiIntegrationTest.java `
        phase4.md
git status
git commit -m "Complete TrainConcierge Phase 4 disruption monitoring"
git push
```

If the branch has no upstream: `git push -u origin HEAD`.

**Recommended commit message**: `Complete TrainConcierge Phase 4 disruption monitoring`
