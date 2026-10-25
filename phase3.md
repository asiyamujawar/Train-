# TrainConcierge — Phase 3: Train Running Status and Disruption Simulation (MySQL)

## 1. Objective and implemented functionality

Simulated (demonstration-only) running status for a train on a specific service date, with a full, immutable transition history.

- `TrainRunningStatus` enum: `ON_TIME`, `DELAYED`, `CANCELLED`.
- Current status entity `TrainStatus` — exactly one row per `(train, serviceDate)`.
- Append-only `TrainStatusHistory` log — one row per initial status and per meaningful change.
- Status operations: set on time, set delayed, cancel, restore to on time, list, read history.
- Current status + history are written **atomically** in one transaction.
- These endpoints are **simulation/administrative** operations. They are **not** connected to Indian Railways or any live system. Authentication/RBAC is a later phase.

## 2. Entity design and status-transition rules

- `train_statuses` — current status. Unique `(train_id, service_date)`.
- `train_status_history` — append-only. `previous_*` columns are `NULL` only for the initial record.
- `StatusSource` enum: `SIMULATION`, `ADMIN`, `SYSTEM`.

Rules enforced in `TrainStatusService`:

| Rule | Behaviour |
| :--- | :--- |
| `ON_TIME` | `delayMinutes` must be 0 |
| `DELAYED` | `delayMinutes` must be > 0 |
| `CANCELLED` | always stores `delayMinutes = 0` (a cancellation is never a delay) |
| Negative delay | rejected (`400`) |
| Unknown/inactive train | `404` / `400` |
| Unknown station id | `404` |
| No status record yet | `404` “No running status reported…” — missing data is **never** treated as on time |
| Unchanged status + delay + station + reason | no duplicate history row is written |
| Every write | refreshes `last_updated_at` (server-generated) |
| Timestamps | always generated on the server; clients cannot supply them |
| History rows | never accepted from public APIs (cannot be spoofed) |
| Concurrency | `uk_train_statuses_train_date` prevents duplicate current rows; a violation maps to `409` |

## 3. Files created / modified

New:

- [TrainRunningStatus.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/TrainRunningStatus.java)
- [StatusSource.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/StatusSource.java)
- [TrainStatus.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/TrainStatus.java)
- [TrainStatusHistory.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/TrainStatusHistory.java)
- [TrainStatusRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/TrainStatusRepository.java)
- [TrainStatusHistoryRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/TrainStatusHistoryRepository.java)
- [TrainStatusRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/TrainStatusRequest.java)
- [TrainStatusActionRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/TrainStatusActionRequest.java)
- [TrainStatusSimulationRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/TrainStatusSimulationRequest.java)
- [TrainStatusResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/TrainStatusResponse.java)
- [TrainStatusHistoryResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/TrainStatusHistoryResponse.java)
- [StatusUpsertResult.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/StatusUpsertResult.java)
- [TrainStatusService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/TrainStatusService.java)
- [TrainStatusController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/TrainStatusController.java)
- [TrainSimulationController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/TrainSimulationController.java)
- [V5__train_status_simulation.sql](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/db/migration/V5__train_status_simulation.sql)
- [TrainStatusApiIntegrationTest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/test/java/com/trainconcierge/trainstatus/TrainStatusApiIntegrationTest.java)

No Phase 0–2 file was modified.

## 4. Flyway migration

`V5__train_status_simulation.sql` (additive only):

- `train_statuses` — FKs to `trains` and `stations`; unique `(train_id, service_date)`; indexes on `train_id`, `service_date`, `status`, `delay_minutes`.
- `train_status_history` — FKs to `trains` and `stations`; index on `(train_id, service_date, changed_at)`.

## 5. MySQL setup and startup

Database setup is unchanged from Phase 0 (`trainconcierge_db`, MySQL 8.x). The V5 migration runs automatically on startup.

```powershell
# 1. Make sure MySQL is running, then:
$env:DB_USERNAME="root"
$env:DB_PASSWORD="P@t!3nc3"

# 2. Start the app (Flyway applies V5 automatically)
mvn spring-boot:run

# 3. Run the test suite
mvn test

# 4. Build a runnable jar
mvn clean package
java -jar target/trainconcierge-0.0.1-SNAPSHOT.jar
```

## 6. API reference

Base URL: `http://localhost:8080/api/v1` · Headers: `Content-Type: application/json`

| # | Method | URL | Purpose |
| :-- | :--- | :--- | :--- |
| A1 | GET | `/trains/{trainId}/status?serviceDate=YYYY-MM-DD` | Current status (`404` if not reported) |
| A2 | GET | `/train-statuses?trainId=&serviceDate=&status=&minDelayMinutes=` | Paginated list (filters optional) |
| A3 | GET | `/trains/{trainId}/status-history?serviceDate=YYYY-MM-DD` | History, oldest first |
| B1 | PUT | `/trains/{trainId}/status` | Create (`201`) or update (`200`) |
| B2 | POST | `/trains/{trainId}/status/on-time` | Set `ON_TIME` |
| B3 | POST | `/trains/{trainId}/status/delay` | Set `DELAYED` (positive delay) |
| B4 | POST | `/trains/{trainId}/status/cancel` | Set `CANCELLED` |
| B5 | POST | `/trains/{trainId}/status/restore` | Restore to `ON_TIME` |
| C1 | POST | `/simulations/train-status` | Convenience wrapper over the same service |

### B1. PUT `/trains/{trainId}/status`

Body:
```json
{
  "serviceDate": "2026-11-15",
  "status": "DELAYED",
  "delayMinutes": 90,
  "currentStationId": 2,
  "reason": "Demonstration delay caused by operational disruption"
}
```
`201 Created` on first write, `200 OK` on update. Example response:
```json
{
  "id": 1,
  "trainId": 1,
  "trainNumber": "12951",
  "trainName": "Mumbai Rajdhani Express (demo)",
  "serviceDate": "2026-11-15",
  "status": "DELAYED",
  "delayMinutes": 90,
  "currentStation": null,
  "expectedDepartureTime": null,
  "expectedArrivalTime": null,
  "reason": "Demonstration delay caused by operational disruption",
  "lastUpdatedAt": "2026-10-25T18:30:00Z",
  "createdAt": "2026-10-25T18:30:00Z",
  "updatedAt": "2026-10-25T18:30:00Z"
}
```

### B2–B5 action endpoints

Body (`delayMinutes` required and > 0 only for `/delay`):
```json
{ "serviceDate": "2026-11-15", "delayMinutes": 90, "reason": "Demo" }
```

### A1. GET `/trains/{trainId}/status?serviceDate=2026-11-15`

`200 OK` with the object above, or `404`:
```json
{
  "timestamp": "2026-10-25T18:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "No running status reported for train 1 on 2026-11-15",
  "path": "/api/v1/trains/1/status"
}
```

### A3. GET `/trains/{trainId}/status-history?serviceDate=2026-11-15`

`200 OK`, oldest first:
```json
[
  { "id": 1, "trainId": 1, "serviceDate": "2026-11-15", "previousStatus": null,
    "newStatus": "ON_TIME", "previousDelayMinutes": null, "newDelayMinutes": 0,
    "reason": null, "source": "SIMULATION", "changedAt": "2026-10-25T18:00:00Z" },
  { "id": 2, "trainId": 1, "serviceDate": "2026-11-15", "previousStatus": "ON_TIME",
    "newStatus": "DELAYED", "previousDelayMinutes": 0, "newDelayMinutes": 90,
    "reason": "Demo", "source": "SIMULATION", "changedAt": "2026-10-25T18:30:00Z" }
]
```

### C1. POST `/simulations/train-status`

```json
{ "trainId": 1, "serviceDate": "2026-11-15", "status": "CANCELLED", "reason": "Demo cancellation" }
```

## 7. Demonstration workflow

```powershell
$base = "http://localhost:8080/api/v1"
$date = "2026-11-15"
```

1. Get a real train id.
```powershell
Invoke-RestMethod "$base/trains" | ConvertTo-Json -Depth 4   # copy an "id"
$trainId = 1
```

2. Create an on-time status.
```powershell
Invoke-RestMethod -Method Put "$base/trains/$trainId/status" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; status="ON_TIME"; delayMinutes=0 } | ConvertTo-Json)
```

3. Change it to a 90-minute delay.
```powershell
Invoke-RestMethod -Method Put "$base/trains/$trainId/status" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; status="DELAYED"; delayMinutes=90; reason="Demo delay" } | ConvertTo-Json)
```

4. Read the current status.
```powershell
Invoke-RestMethod "$base/trains/$trainId/status?serviceDate=$date" | ConvertTo-Json
```

5. Read the history (expect 2 rows, `ON_TIME` then `DELAYED`).
```powershell
Invoke-RestMethod "$base/trains/$trainId/status-history?serviceDate=$date" | ConvertTo-Json -Depth 4
```

6. Cancel the train and verify.
```powershell
Invoke-RestMethod -Method Post "$base/trains/$trainId/status/cancel" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; reason="Demo cancellation" } | ConvertTo-Json)
Invoke-RestMethod "$base/trains/$trainId/status?serviceDate=$date" | ConvertTo-Json
```

7. Restore to on time, then confirm all 4 transitions remain.
```powershell
Invoke-RestMethod -Method Post "$base/trains/$trainId/status/restore" -ContentType "application/json" `
  -Body (@{ serviceDate=$date } | ConvertTo-Json)
Invoke-RestMethod "$base/trains/$trainId/status-history?serviceDate=$date" | ConvertTo-Json -Depth 4
```

## 8. Invalid requests

| Scenario | Expected |
| :--- | :--- |
| `delayMinutes: -5` | `400` validation error |
| `status: DELAYED, delayMinutes: 0` | `400` “A DELAYED status must have a positive delay” |
| `status: ON_TIME, delayMinutes: 10` | `400` “An ON_TIME status must have a zero delay” |
| Unknown `trainId` | `404` “Train not found with id: …” |
| Unknown `currentStationId` | `404` “Station not found with id: …” |
| `GET status` with no record | `404` “No running status reported…” |
| `restore` when already `ON_TIME` | `400` “Train is already ON_TIME for the given service date” |
| `/delay` without a positive `delayMinutes` | `400` “A delay operation requires a positive delayMinutes value” |

## 9. Automated test results

Ran `mvn test` on 2026-10-25 against MySQL 8.0.46.

- **Total: 68 · Failures: 0 · Errors: 0 · Skipped: 0 · BUILD SUCCESS**
- New: `TrainStatusApiIntegrationTest` — 18 tests (initial ON_TIME, DELAYED, CANCELLED, larger delay, restore, read current, `404` not-reported, ascending history, negative/zero/positive-delay rejections, unknown train + station, no duplicate current row, no duplicate history, transactional rollback of status+history, filtered list, simulation endpoint).
- Phases 1–2 suites still pass (`StationApiIntegrationTest` 7, `TrainTimetableApiIntegrationTest` 15, `PassengerApiIntegrationTest` 6, `JourneyApiIntegrationTest` 5, plus controller/validation tests).

## 10. Troubleshooting

- **Migration error**: check `flyway_schema_history`; never edit an applied `V5`. Restore a clean DB only if needed.
- **Duplicate status rows**: the unique key `uk_train_statuses_train_date` prevents them; a duplicate insert surfaces as `409`. Never widen this to multiple rows per train/date.
- **Invalid delay values**: use `0` for `ON_TIME`, `> 0` for `DELAYED`, and omit delay for `CANCELLED`.
- **Transaction failures**: current status and history are written in one transaction; a failure rolls back both. Do not wrap status writes in separate transactions.
- **`ENOTFOUND` in Postman**: prefix local requests with `http://localhost:8080`.

## 11. Git review and push

Review every changed file first and exclude credentials, generated files, and unrelated work.

```powershell
git status
git diff
git add src/main/java/com/trainconcierge/entity/TrainRunningStatus.java `
        src/main/java/com/trainconcierge/entity/StatusSource.java `
        src/main/java/com/trainconcierge/entity/TrainStatus.java `
        src/main/java/com/trainconcierge/entity/TrainStatusHistory.java `
        src/main/java/com/trainconcierge/repository/TrainStatusRepository.java `
        src/main/java/com/trainconcierge/repository/TrainStatusHistoryRepository.java `
        src/main/java/com/trainconcierge/dto/TrainStatusRequest.java `
        src/main/java/com/trainconcierge/dto/TrainStatusActionRequest.java `
        src/main/java/com/trainconcierge/dto/TrainStatusSimulationRequest.java `
        src/main/java/com/trainconcierge/dto/TrainStatusResponse.java `
        src/main/java/com/trainconcierge/dto/TrainStatusHistoryResponse.java `
        src/main/java/com/trainconcierge/service/StatusUpsertResult.java `
        src/main/java/com/trainconcierge/service/TrainStatusService.java `
        src/main/java/com/trainconcierge/controller/TrainStatusController.java `
        src/main/java/com/trainconcierge/controller/TrainSimulationController.java `
        src/main/resources/db/migration/V5__train_status_simulation.sql `
        src/test/java/com/trainconcierge/trainstatus/TrainStatusApiIntegrationTest.java `
        phase3.md
git status
git commit -m "Complete TrainConcierge Phase 3 train status simulation"
git push
```

If the branch has no upstream:

```powershell
git push -u origin HEAD
```

**Recommended commit message**: `Complete TrainConcierge Phase 3 train status simulation`
