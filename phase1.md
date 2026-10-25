# TrainConcierge — Phase 1: Railway Master Data and Train Timetables (MySQL)

This document describes **Phase 1 only** of the TrainConcierge project. It builds on the
Phase 0 foundation (which is documented in `phase0.md` and remains unchanged) and adds
railway master data (stations, trains, train stops) plus a source-to-destination timetable
search.

---

## 1. Phase objective and features implemented

**Objective:** model the railway master data that every later phase (journeys, disruption
monitoring, recommendations) will depend on, expose it through clean, validated REST APIs,
and provide an efficient timetable search that respects the physical order of stops.

**Implemented in Phase 1**

- **Station master data**: create, read by id, read by station code, filtered/paginated list,
  update, and soft deactivation. Station codes are normalized to uppercase and unique.
- **Train master data**: create, read by id, read by train number, filtered/paginated list,
  update, and soft deactivation. Train numbers are strings (leading zeros preserved) and unique.
- **Train stops (timetable legs)**: add, list (ordered by `stopSequence`), update and delete a
  stop of a specific train, with duplicate-sequence and duplicate-station protection.
- **Timetable retrieval**: `GET /api/v1/trains/{trainId}/timetable` returns the train, its
  source/destination stations and its ordered stops with arrival/departure times, day offsets
  and distances.
- **Timetable search**: `GET /api/v1/timetables/search?sourceStationCode=...&destinationStationCode=...`
  returns only the active trains that stop at **both** stations **in the requested order**
  (source stop sequence strictly lower than destination stop sequence).
- **Deterministic seed data** (six stations, four trains, twelve stops) loaded through Flyway,
  including both a successful search case and its reversed (empty) counterpart.
- **Consistent error handling** on top of Phase 0's `GlobalExceptionHandler` (400 / 404 / 409),
  including invalid pagination/sorting parameters and database constraint violations.

**Deliberately NOT implemented (later phases):** passengers, journeys/bookings, disruption
monitoring, journey impact analysis, alternative recommendations, hotels, notifications,
JWT authentication and dashboards. No AI/ML, microservices or live railway APIs were added.

---

## 2. Design decisions for entities and relationships

### 2.1 Overall architecture

Layered architecture is used throughout:

```
Controller  ->  Service  ->  Repository  ->  Entity
   (DTO in/out)  (business rules)  (queries)   (JPA mapping)
```

- Controllers only translate HTTP ⇄ DTOs; they contain no business logic.
- Services own validation that requires database state (uniqueness, existence, ordering).
- Repositories own all query logic.
- **JPA entities are never returned from a controller** — every response is a Java `record` DTO.

### 2.2 `Station`

- `id` (`BIGINT`, `IDENTITY`), `stationCode` (unique, max 10 chars), `name`, `city`,
  optional `state`, `active`, `createdAt` / `updatedAt`.
- `stationCode` is **normalized to uppercase** in the service (`trim().toUpperCase(Locale.ROOT)`)
  before every read and write, so `csmt`, `Csmt` and `CSMT` all resolve to the same station.
- Deactivation (`PATCH /{id}/deactivate`) is a **soft delete**: the row stays so that historical
  timetables keep working; `active=false` merely excludes the station from new searches.

### 2.3 `Train`

- `trainNumber` is stored as a **`VARCHAR(10)`**, not a number, so fictional numbers with leading
  zeros (e.g. `00123`) survive round-trips.
- `sourceStation` / `destinationStation` are `@ManyToOne(fetch = LAZY)` references to `Station`.
  LAZY is important: it prevents recursive serialization and avoids loading stations that a
  given request does not need. When station details *are* needed, repositories `join fetch` them
  (see §6) and the DTO mapper converts them to `StationSummaryResponse`.
- Business rules in the service layer: source ≠ destination, and both stations must exist
  (and be referenced by id, never by free-text name).

### 2.4 `TrainStop`

- `@ManyToOne(fetch = LAZY)` to `Train` and to `Station`; `stopSequence` is a positive `Integer`.
- Scheduled times use **`LocalTime`** (mapped to MySQL `TIME`) plus an explicit **`dayOffset`**
  (non-negative `Integer`) instead of a `LocalDateTime`. This is the standard railway model:
  a timetable publishes "17:00 day 1, 07:00 day 2" rather than an absolute timestamp, and it
  keeps the schedule independent of any particular travel date (a Phase 1 concern only —
  date-specific running is out of scope).
- `distanceFromSourceKm` is an optional non-negative `Integer` used to compute the journey
  distance between two stops of the same train.
- A stop must define **at least one** of `arrivalTime` / `departureTime`; this cross-field rule
  lives in the service because it needs both values together.

### 2.5 `TrainSchedule` — deliberately NOT implemented

The Phase 1 brief allowed a `TrainSchedule` entity "only if it adds a useful distinction".
It was **intentionally omitted**, because:

1. Everything Phase 1 needs to express is already expressible with `TrainStop` + `dayOffset`:
   the ordered timetable, overnight legs and distances.
2. The only *extra* information `TrainSchedule` could carry here is operating days and an
   effective period. Phase 1 has **no date-aware feature** that would consume it (the brief
   explicitly forbids inventing date filtering that the schedule model cannot support), so the
   entity would be dead weight — exactly the "redundant entity" the brief warns against.
3. Adding it later is additive and non-breaking: a new `train_schedules` table plus a migration
   can be introduced in the phase that actually needs running-days (disruption/recommendation
   phases), without touching `TrainStop`.

`TrainStop` is therefore the single source of truth for scheduled arrival/departure data, and no
timetable data is duplicated anywhere.

### 2.6 Relationship summary

```
Station 1 ──── * Train          (source / destination, two FKs, RESTRICT)
Train   1 ──── * TrainStop      (ON DELETE CASCADE)
Station 1 ──── * TrainStop      (RESTRICT)
TrainStop is unique on (train_id, stop_sequence) and on (train_id, station_id)
```

---

## 3. Every file created or modified

### 3.1 Created — main sources

| File | Purpose |
| --- | --- |
| `src/main/resources/db/migration/V2__railway_master_data.sql` | Creates `stations`, `trains`, `train_stops` with unique keys, indexes and FKs. |
| `src/main/resources/db/migration/V3__seed_railway_sample_data.sql` | Deterministic demo dataset (idempotent `INSERT IGNORE`). |
| `src/main/java/com/trainconcierge/entity/Station.java` | `Station` JPA entity. |
| `src/main/java/com/trainconcierge/entity/Train.java` | `Train` JPA entity (LAZY station references, string train number). |
| `src/main/java/com/trainconcierge/entity/TrainStop.java` | `TrainStop` JPA entity (`LocalTime` times + `dayOffset`). |
| `src/main/java/com/trainconcierge/entity/TrainType.java` | `TrainType` enum (`EXPRESS`, `SUPERFAST`, `PASSENGER`, `MAIL`, `RAJDHANI`, `SHATABDI`, `DURONTO`, `LOCAL`), stored as `STRING`. |
| `src/main/java/com/trainconcierge/exception/ConflictException.java` | Runtime exception → HTTP 409. |
| `src/main/java/com/trainconcierge/exception/InvalidRequestException.java` | Runtime exception → HTTP 400 for business-rule violations. |
| `src/main/java/com/trainconcierge/repository/StationRepository.java` | `findByStationCode`, existence checks, paginated null-tolerant `search(...)`. |
| `src/main/java/com/trainconcierge/repository/TrainRepository.java` | `findByTrainNumber`, `findDetailById/Bynumber` (join fetch stations), paginated `search(...)`. |
| `src/main/java/com/trainconcierge/repository/TrainStopRepository.java` | Ordered stops with station join fetch, duplicate checks, and the timetable-search tuple query. |
| `src/main/java/com/trainconcierge/dto/StationRequest.java` | Validated create/update payload for stations. |
| `src/main/java/com/trainconcierge/dto/StationResponse.java` | Full station response. |
| `src/main/java/com/trainconcierge/dto/StationSummaryResponse.java` | Compact station response embedded in train/stop/timetable responses. |
| `src/main/java/com/trainconcierge/dto/TrainRequest.java` | Validated create/update payload for trains. |
| `src/main/java/com/trainconcierge/dto/TrainResponse.java` | Train response with source/destination summaries. |
| `src/main/java/com/trainconcierge/dto/TrainStopRequest.java` | Validated payload for a train stop. |
| `src/main/java/com/trainconcierge/dto/TrainStopResponse.java` | Train-stop response (`trainId` passed in to avoid touching the lazy `Train`). |
| `src/main/java/com/trainconcierge/dto/TimetableResponse.java` | `{ train, stops[] }` — the full timetable. |
| `src/main/java/com/trainconcierge/dto/TimetableSearchResult.java` | One row of a timetable search (times, day offsets, distance). |
| `src/main/java/com/trainconcierge/dto/TimetableSearchResponse.java` | `{ sourceStationCode, destinationStationCode, resultCount, results[] }`. |
| `src/main/java/com/trainconcierge/dto/PageResponse.java` | Generic pagination envelope (so Spring Data `Page` is never serialized directly). |
| `src/main/java/com/trainconcierge/service/StationService.java` | Station business rules (normalization, uniqueness, soft delete). |
| `src/main/java/com/trainconcierge/service/TrainService.java` | Train business rules (station existence, distinct stations, uniqueness). |
| `src/main/java/com/trainconcierge/service/TrainStopService.java` | Stop rules (active station, schedule fields, duplicate sequence/station). |
| `src/main/java/com/trainconcierge/service/TimetableService.java` | Timetable assembly and the ordered source→destination search. |
| `src/main/java/com/trainconcierge/controller/StationController.java` | `/api/v1/stations` endpoints. |
| `src/main/java/com/trainconcierge/controller/TrainController.java` | `/api/v1/trains` endpoints. |
| `src/main/java/com/trainconcierge/controller/TrainStopController.java` | `/api/v1/trains/{trainId}/stops` endpoints. |
| `src/main/java/com/trainconcierge/controller/TimetableController.java` | `/timetable` and `/api/v1/timetables/search` endpoints. |

### 3.2 Created — tests

| File | Purpose |
| --- | --- |
| `src/test/java/com/trainconcierge/station/StationApiIntegrationTest.java` | 7 tests: station CRUD, normalization, duplicates, paging/filtering, deactivation (real MySQL). |
| `src/test/java/com/trainconcierge/timetable/TrainTimetableApiIntegrationTest.java` | 15 tests: train creation, stop ordering, duplicates, timetable retrieval and ordered search (real MySQL). |
| `src/test/java/com/trainconcierge/controller/StationControllerWebMvcTest.java` | 6 slice tests for the station web layer (no database needed). |

### 3.3 Modified

| File | Change |
| --- | --- |
| `src/main/java/com/trainconcierge/exception/GlobalExceptionHandler.java` | Added handlers for `InvalidRequestException` (400), `ConflictException` (409), `DataIntegrityViolationException` (409), `PropertyReferenceException` (400) and `InvalidDataAccessApiUsageException` (400 for invalid sort/pagination). |
| `src/main/resources/application.yml` | Added `spring.data.web.pageable.default-page-size: 20` / `max-page-size: 100`; updated `info.app.description` to mention Phase 1. |
| `src/main/java/com/trainconcierge/config/OpenApiConfig.java` | Updated the API description to mention Phase 1 master data and timetables. |
| `phase1.md` | This document (the only new Markdown file; `phase0.md` is untouched). |

---

## 4. Flyway migration name and database changes

Two **additive** migrations were added; no existing migration was edited.

### `V2__railway_master_data.sql`

Creates three `InnoDB` / `utf8mb4` tables:

- **`stations`**
  - `id BIGINT AUTO_INCREMENT` PK, `station_code VARCHAR(10) NOT NULL`,
    `name VARCHAR(120) NOT NULL`, `city VARCHAR(80) NOT NULL`, `state VARCHAR(80) NULL`,
    `active BOOLEAN NOT NULL DEFAULT TRUE`,
    `created_at` / `updated_at DATETIME(6)` with `CURRENT_TIMESTAMP(6)`.
  - `CONSTRAINT uk_stations_station_code UNIQUE (station_code)`;
    indexes on `name`, `city`, `active`.
- **`trains`**
  - `id BIGINT AUTO_INCREMENT` PK, `train_number VARCHAR(10) NOT NULL`,
    `name VARCHAR(150) NOT NULL`, `train_type VARCHAR(30) NULL`,
    `source_station_id BIGINT NOT NULL`, `destination_station_id BIGINT NOT NULL`,
    `active`, `created_at`, `updated_at`.
  - `CONSTRAINT uk_trains_train_number UNIQUE (train_number)`;
    `fk_trains_source_station` / `fk_trains_destination_station` → `stations(id)`
    (default `RESTRICT`, protecting historical data);
    indexes on `name`, `train_type`, `source_station_id`, `destination_station_id`, `active`.
- **`train_stops`**
  - `id BIGINT AUTO_INCREMENT` PK, `train_id`, `station_id`, `stop_sequence INT NOT NULL`,
    `arrival_time TIME NULL`, `departure_time TIME NULL`, `day_offset INT NOT NULL DEFAULT 0`,
    `distance_from_source_km INT NULL`.
  - `uk_train_stops_train_sequence UNIQUE (train_id, stop_sequence)`,
    `uk_train_stops_train_station UNIQUE (train_id, station_id)`,
    `fk_train_stops_train` → `trains(id) ON DELETE CASCADE`,
    `fk_train_stops_station` → `stations(id)` (`RESTRICT`);
    indexes on `station_id` and `(train_id, stop_sequence)`.

### `V3__seed_railway_sample_data.sql`

Inserts the demonstration dataset with `INSERT IGNORE` (safe to re-run, relies on the unique
keys above, and never deletes or resets user data):

- Stations: `CSMT`, `PUNE`, `SUR`, `SBC`, `NDLS`, `AGC`.
- Trains (fictional demo numbers): `12951` (CSMT→NDLS), `12123` (CSMT→PUNE),
  `11013` (PUNE→SUR), `12627` (NDLS→SBC).
- Stops: 4 + 2 + 2 + 4 = 12 rows, with correct `stop_sequence`, times, `day_offset`
  (including overnight legs) and distances.

The file header states clearly that **the data is illustrative only** — station names are real
Indian stations, but the train numbers, timings and distances are fictional demo values and do
**not** represent any authoritative or live railway timetable.

Flyway validates cleanly against Hibernate's `ddl-auto: validate`, which proves the migrations
and the JPA entities are in exact agreement.

---

## 5. MySQL prerequisites and database setup

Phase 1 uses exactly the same MySQL instance configured in Phase 0 (see `phase0.md`).

1. **MySQL 8.x must be running locally.**
   ```sql
   SELECT VERSION();   -- expected: 8.0.x
   ```
2. Create the database once (Flyway creates the *tables*, not the database):
   ```sql
   CREATE DATABASE IF NOT EXISTS trainconcierge_db
       CHARACTER SET utf8mb4
       COLLATE utf8mb4_0900_ai_ci;
   ```
3. `spring.jpa.hibernate.ddl-auto` stays `validate`. Flyway owns the schema and will apply
   `V1` (Phase 0 baseline), `V2` (railway master data) and `V3` (seed data) on startup.
4. Optional verification after the first successful start:
   ```sql
   SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
   SELECT COUNT(*) FROM stations;    -- 6
   SELECT COUNT(*) FROM trains;      -- 4
   SELECT COUNT(*) FROM train_stops; -- 12
   ```

---

## 6. Environment variables and starting the backend

### 6.1 Environment variables

`src/main/resources/application.yml` reads the same variables as Phase 0, with local defaults:

| Variable | Default | Meaning |
| --- | --- | --- |
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `trainconcierge_db` | Database name |
| `DB_USERNAME` | `root` | MySQL user |
| `DB_PASSWORD` | *(see note)* | MySQL password |
| `SERVER_PORT` | `8080` | HTTP port |

> **Note / security:** the tracked `application.yml` currently contains a *fallback* password
> (`${DB_PASSWORD:...}`). Anyone running this project should instead export `DB_PASSWORD`
> (and keep `application-local.yml`, which is git-ignored, for local credentials). Never commit
> real credentials.

PowerShell example:

```powershell
$env:DB_HOST="localhost"; $env:DB_PORT="3306"; $env:DB_NAME="trainconcierge_db"
$env:DB_USERNAME="root";  $env:DB_PASSWORD="<your-password>"
```

### 6.2 Start the backend

```powershell
cd C:\Users\HP\Documents\trae_projects\Railway_New
mvn spring-boot:run
```

Or build and run the packaged jar:

```powershell
mvn clean package
java -jar target\trainconcierge-0.0.1-SNAPSHOT.jar
```

Useful URLs once started:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

---

## 7. API testing guide (Postman & Terminal)

**Base URL**: `http://localhost:8080`  
*Important: Do not use `/api/...` alone in Postman; always prefix with the host or use the `{{baseUrl}}` variable to avoid `ENOTFOUND` errors.*

### 7.1 Postman instructions

Create a Postman environment with `baseUrl = http://localhost:8080`.
Send `Content-Type: application/json` for every request with a body.

#### Station APIs
- **POST** `{{baseUrl}}/api/v1/stations` — Create (Body: `{"stationCode":"NGP", "name":"Nagpur", "city":"Nagpur", "active":true}`)
- **GET** `{{baseUrl}}/api/v1/stations/{id}` — Get by ID
- **GET** `{{baseUrl}}/api/v1/stations/code/CSMT` — Get by code
- **GET** `{{baseUrl}}/api/v1/stations?city=Mumbai&page=0&size=5` — List/Search
- **PUT** `{{baseUrl}}/api/v1/stations/{id}` — Update
- **PATCH** `{{baseUrl}}/api/v1/stations/{id}/deactivate` — Soft delete

#### Train APIs
- **POST** `{{baseUrl}}/api/v1/trains` — Create (Body: `{"trainNumber":"22119", "name":"Demo", "trainType":"EXPRESS", "sourceStationId":1, "destinationStationId":2, "active":true}`)
- **GET** `{{baseUrl}}/api/v1/trains/{id}` — Get with station details
- **GET** `{{baseUrl}}/api/v1/trains/number/11013` — Get by number
- **GET** `{{baseUrl}}/api/v1/trains?trainType=RAJDHANI` — List/Filter
- **PATCH** `{{baseUrl}}/api/v1/trains/{id}/deactivate` — Deactivate

#### Timetable & Search APIs
- **POST** `{{baseUrl}}/api/v1/trains/{trainId}/stops` — Add stop (Body: `{"stationId":3, "stopSequence":2, "arrivalTime":"12:30:00", "dayOffset":0}`)
- **GET** `{{baseUrl}}/api/v1/trains/{trainId}/timetable` — Full ordered timetable
- **GET** `{{baseUrl}}/api/v1/timetables/search?sourceStationCode=PUNE&destinationStationCode=SUR` — Search trains

### 7.2 Terminal testing commands (cURL & PowerShell)

If you prefer testing from the command line, use these examples.

#### PowerShell (Recommended for Windows)
```powershell
# 1. Check System Status
Invoke-WebRequest "http://localhost:8080/api/v1/system/status" | Select-Object -ExpandProperty Content

# 2. Search Timetable (PUNE to SUR)
Invoke-WebRequest "http://localhost:8080/api/v1/timetables/search?sourceStationCode=PUNE&destinationStationCode=SUR" | Select-Object -ExpandProperty Content

# 3. Get Train Timetable
Invoke-WebRequest "http://localhost:8080/api/v1/trains/1/timetable" | Select-Object -ExpandProperty Content

# 4. List Stations (Paginated)
Invoke-WebRequest "http://localhost:8080/api/v1/stations?size=2&sort=stationCode,asc" | Select-Object -ExpandProperty Content
```

#### cURL (Bash/CMD)
```bash
# 1. System Status
curl -X GET http://localhost:8080/api/v1/system/status

# 2. Search Timetable
curl -X GET "http://localhost:8080/api/v1/timetables/search?sourceStationCode=PUNE&destinationStationCode=SUR"

# 3. Create Station (JSON)
curl -X POST http://localhost:8080/api/v1/stations \
     -H "Content-Type: application/json" \
     -d '{"stationCode":"BPL","name":"Bhopal Junction","city":"Bhopal","active":true}'
```

### 7.3 System & utility APIs (Phase 0)

These endpoints verify the application health and basic connectivity.

- **GET** `http://localhost:8080/api/v1/system/status` — Returns app version and DB connection status.
- **POST** `http://localhost:8080/api/v1/system/echo` — Re-validates a message body.
- **GET** `http://localhost:8080/actuator/health` — Spring Boot Actuator health check.

---

## 8. Complete sample workflow

Everything below can be replayed in Postman. Steps 1–3 use the seeded data; steps 4–6 create a
new train from scratch.

1. **Get stations** (or create your own):
   - `GET {{baseUrl}}/api/v1/stations/code/PUNE` → 200, note `id` (seeded: `2`).
   - `GET {{baseUrl}}/api/v1/stations/code/SUR`  → 200, note `id` (seeded: `3`).
2. **Create a train** (optional — a seeded train also works):
   - `POST {{baseUrl}}/api/v1/trains`
     ```json
     { "trainNumber": "22119", "name": "Demo Intercity Express", "trainType": "EXPRESS", "sourceStationId": 2, "destinationStationId": 3, "active": true }
     ```
   - Expect **201 Created**; note the new `id` (call it `{trainId}`).
3. **Add ordered stops** to that train:
   - `POST {{baseUrl}}/api/v1/trains/{trainId}/stops`
     ```json
     { "stationId": 2, "stopSequence": 1, "departureTime": "08:00:00", "dayOffset": 0, "distanceFromSourceKm": 0 }
     ```
     → **201 Created**
   - `POST {{baseUrl}}/api/v1/trains/{trainId}/stops`
     ```json
     { "stationId": 3, "stopSequence": 2, "arrivalTime": "12:30:00", "dayOffset": 0, "distanceFromSourceKm": 264 }
     ```
     → **201 Created**
   - `GET {{baseUrl}}/api/v1/trains/{trainId}/stops` → **200 OK**, two stops ordered 1 then 2.
4. **Retrieve the timetable**:
   - `GET {{baseUrl}}/api/v1/trains/{trainId}/timetable` → **200 OK**, `stops` ordered by
     `stopSequence` with times, day offsets and distances.
5. **Search between two stations**:
   - `GET {{baseUrl}}/api/v1/timetables/search?sourceStationCode=PUNE&destinationStationCode=SUR`
     → **200 OK**, `resultCount` ≥ 1, and the created train (`22119`) plus the seeded `11013`
     appear (ordered by `departureTime`, then `trainNumber`).
6. **Verify the reversed order does NOT match**:
   - `GET {{baseUrl}}/api/v1/timetables/search?sourceStationCode=SUR&destinationStationCode=PUNE`
     → **200 OK** with `"resultCount": 0`, because `SUR` appears *after* `PUNE` in the stop list.

---

## 9. Example invalid requests and expected error responses

All error bodies use the Phase 0 envelope (no stack traces, SQL or credentials are ever leaked).

| # | Request | Status | Body (abridged) |
| --- | --- | --- | --- |
| 1 | `POST /api/v1/stations` with `stationCode: ""` | **400** | `"message":"Validation failed for one or more fields"`, `"fieldErrors":{"stationCode":"...","name":"name must not be blank","city":"city must not be blank"}` |
| 2 | `POST /api/v1/stations` with an existing code (e.g. `csmt`) | **409** | `"message":"Station code 'CSMT' already exists"` |
| 3 | `GET /api/v1/stations/999999` | **404** | `"message":"Station not found with id: 999999"` |
| 4 | `GET /api/v1/stations/code/NOPE` | **404** | `"message":"Station not found with code: NOPE"` |
| 5 | `GET /api/v1/stations/abc` (non-numeric id) | **400** | `"message":"Parameter 'id' has an invalid value"` |
| 6 | `POST /api/v1/trains` with `sourceStationId == destinationStationId` | **400** | `"message":"Source and destination stations must be different"` |
| 7 | `POST /api/v1/trains` with an unknown `sourceStationId` | **404** | `"message":"Station not found with id: ..."` |
| 8 | `POST /api/v1/trains` with an existing `trainNumber` | **409** | `"message":"Train number '...' already exists"` |
| 9 | `POST /api/v1/trains/{id}/stops` with no `arrivalTime` and no `departureTime` | **400** | `"message":"A stop must define at least one of arrivalTime or departureTime"` |
| 10 | `POST /api/v1/trains/{id}/stops` reusing an existing `stopSequence` | **409** | `"message":"Stop sequence 2 already exists for train 12951"` |
| 11 | `POST /api/v1/trains/{id}/stops` reusing an existing `stationId` | **409** | `"message":"Station CSMT is already a stop of train 12951"` |
| 12 | `GET /api/v1/timetables/search?sourceStationCode=PUNE&destinationStationCode=PUNE` | **400** | `"message":"Source and destination stations must be different"` |
| 13 | `GET /api/v1/trains?sort=notAColumn` | **400** | `"message":"Invalid pagination or sorting parameters"` |
| 14 | `DELETE /api/v1/trains/{trainId}/stops/999999` | **404** | `"message":"Stop 999999 not found for train {trainId}"` |
| 15 | `POST /api/v1/stations/1` (unsupported method) | **405** | `"message":"HTTP method POST is not supported for this endpoint. Supported: GET, PUT"` |
| 16 | `GET /api/v1/does-not-exist` | **404** | `"message":"No endpoint found for this request"` |

Live-verified examples from the running application:

```json
// GET /api/v1/trains?sort=notAColumn  -> 400
{"timestamp":"2026-10-25T22:41:59.7942486+05:30","status":400,"error":"Bad Request",
 "message":"Invalid pagination or sorting parameters","path":"/api/v1/trains"}

// GET /api/v1/timetables/search?sourceStationCode=PUNE&destinationStationCode=PUNE -> 400
{"timestamp":"2026-10-25T22:42:07.4344388+05:30","status":400,"error":"Bad Request",
 "message":"Source and destination stations must be different","path":"/api/v1/timetables/search"}

// GET /api/v1/stations/999999 -> 404
{"timestamp":"2026-10-25T22:43:10.9530992+05:30","status":404,"error":"Not Found",
 "message":"Station not found with id: 999999","path":"/api/v1/stations/999999"}
```

---

## 10. Automated tests run and their real results

Tests were executed against the **real local MySQL 8.0.46** database (`trainconcierge_db`).
The integration tests (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`) run
against MySQL and are rolled back, so they never leave data behind. The `@WebMvcTest` slices run
without a database. **No PostgreSQL, H2 or other database was used or substituted.**

Command:

```powershell
mvn clean package
```

Actual result (real output):

```
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- com.trainconcierge.controller.StationControllerWebMvcTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- com.trainconcierge.controller.SystemControllerWebMvcTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- com.trainconcierge.dto.EchoRequestValidationTest
[INFO] Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0 -- com.trainconcierge.station.StationApiIntegrationTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- com.trainconcierge.timetable.TrainTimetableApiIntegrationTest
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- com.trainconcierge.TrainConciergeApplicationTests
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: C:\Users\HP\Documents\trae_projects\Railway_New\target\trainconcierge-0.0.1-SNAPSHOT.jar
[INFO] BUILD SUCCESS
```

Coverage of the required scenarios:

| Required scenario | Covered by |
| --- | --- |
| Station creation and retrieval | `StationApiIntegrationTest`, `StationControllerWebMvcTest` |
| Duplicate station-code rejection | `StationApiIntegrationTest` (409), `StationControllerWebMvcTest` |
| Train creation with valid station references | `TrainTimetableApiIntegrationTest` |
| Rejection of invalid source/destination stations | `TrainTimetableApiIntegrationTest` (400 same-station, 404 unknown station) |
| Train-stop sequence validation | `TrainTimetableApiIntegrationTest` (duplicate sequence → 409) |
| Timetable retrieval with stops in the correct order | `TrainTimetableApiIntegrationTest` (`hasSize(2)`, sequence 1 then 2) |
| Timetable search returning a matching train | `TrainTimetableApiIntegrationTest` (PUNE→SUR returns `11013`, dep 08:00, arr 12:30, 264 km) |
| Timetable search excluding reversed station order | `TrainTimetableApiIntegrationTest` (SUR→PUNE → `resultCount 0`) |
| Missing-resource handling | `StationApiIntegrationTest`, `TrainTimetableApiIntegrationTest` (404s) |
| Pagination and filtering | `StationApiIntegrationTest` (`city` filter, `page/size/sort`, totals asserted) |

Flyway/Hibernate consistency was also proven at runtime by the startup log:

```
Successfully validated 3 migrations
Current version of schema `trainconcierge_db`: 3
Schema `trainconcierge_db` is up to date. No migration necessary.
Initialized JPA EntityManagerFactory for persistence unit 'default'   (ddl-auto: validate passed)
```

---

## 11. Troubleshooting

**Flyway migration failures**
- `FlywayValidateException: Migration checksum mismatch` — a migration already applied was
  edited. Do **not** edit applied migrations; restore the file or add a new `V4__...` migration.
  For a throwaway local database only, `flyway repair` (or dropping and recreating
  `trainconcierge_db`) resets the checksums.
- `Found non-empty schema ... without schema history table` — the database contains tables but
  no Flyway history (e.g. it was created outside Flyway). Recreate the database and let Flyway
  run, or use `baseline-on-migrate` deliberately (Phase 1 keeps it `false`).
- `Table 'trainconcierge_db.xxx' doesn't exist` on startup — the migration did not run; check
  `spring.flyway.locations` and the `db/migration` folder.
- If you see `Schema-validation: missing table/column` — the entities and the V2 migration have
  drifted; fix whichever is wrong and add a **new** migration for real databases.

**Foreign-key violations (`foreign key constraint fails`)**
- Deleting a station that is referenced by `trains` or `train_stops` is blocked (`RESTRICT`).
  Deactivate the station/stop instead of deleting it.
- Creating a train or stop with a non-existent `stationId` (or a stop referencing a non-existent
  train) fails. Verify the ids with `GET /api/v1/stations/{id}` /
  `GET /api/v1/trains/{id}` first. The API normally reports these as **404** before they reach
  the database.

**Duplicate values (409)**
- Duplicate station code: station codes are uppercased, so `csmt` collides with `CSMT`.
- Duplicate train number: train numbers are unique strings.
- Duplicate stop: `(train_id, stop_sequence)` and `(train_id, station_id)` are both unique, so
  you cannot repeat a sequence number or list the same station twice for one train.
- If a raw `DataIntegrityViolationException` ever escapes the service checks, the global handler
  still returns **409** with a generic message (never the SQL text).

**Invalid timetable ordering / empty search results**
- A search returns nothing when the source stop's `stopSequence` is **not lower** than the
  destination stop's `stopSequence`, when either station is inactive, or when the train is
  inactive. This is intentional (see §6 of the brief): reversed order must not match.
- If a train does not appear, confirm it has a stop for **both** stations and that the sequences
  are ordered correctly (`GET /api/v1/trains/{trainId}/stops`).
- Missing `arrivalTime`/`departureTime` are returned as `null` and are never invented; a search
  result will simply show `null` for a time the timetable does not define.

**Invalid sort/pagination**
- `GET /api/v1/trains?sort=notAColumn` returns **400** `"Invalid pagination or sorting
  parameters"`. Use a real entity property such as `trainNumber`, `name`, `trainType` or `id`.
- `size` is capped at 100 and defaults to 20.

---

## 12. Git commands for reviewing, staging, committing and pushing

> **Do not commit or push automatically.** Review everything first. Current `git status`
> shows the Phase 1 sources as modified/untracked — including a modified `application.yml`
> that carries a **fallback database password**. Consider removing that fallback (or relying on
> `DB_PASSWORD` / the git-ignored `application-local.yml`) before committing, and make sure
> `application-local.yml` and `target/` remain untracked.

```powershell
# 1. Review what changed
git status
git diff

# 2. Stage only the reviewed Phase 1 files (examples)
git add src/main/java/com/trainconcierge
git add src/main/resources/db/migration
git add src/main/resources/application.yml
git add src/test/java/com/trainconcierge
git add phase1.md

# 3. Confirm the staged set
git status

# 4. Commit
git commit -m "Complete TrainConcierge Phase 1 railway master data"

# 5. Push
git push
# If the current branch has no upstream yet:
git push -u origin HEAD
```

---

## 13. Recommended commit message

```
Complete TrainConcierge Phase 1 railway master data
```
