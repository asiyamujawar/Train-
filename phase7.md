# TrainConcierge — Phase 7: Hotel Recommendations (MySQL)

## 1. Objective and implemented features

Help a passenger whose journey is disrupted decide whether an **overnight hotel stay is likely to be needed**, and — when it is — return a **deterministic, ranked list of hotels** near the station where the passenger is stranded. The whole module is plain deterministic Java over Phases 1–6 data: no ML, no external AI API, and **no live hotel API**. It reuses the existing entities (`Station`, `Train`, `TrainStop`, `Passenger`, `Journey`, `JourneyLeg`, `TrainStatus`, `DisruptionEvent`, `JourneyImpact`, `TrainRecommendation`) and adds no duplicate journey/passenger/station/train/disruption model.

> **A hotel recommendation is not a booking.** TrainConcierge has no booking or live-availability integration. Every hotel carries an explicit availability status and every generated explanation states that availability is not confirmed. This phase never claims that a room is available, that a price is guaranteed, or that a reservation has been made.

**Implemented**

- `GET /api/v1/journeys/{journeyId}/hotel-recommendations` evaluates a journey (transient, nothing persisted) and returns the decision outcome, the stranded station (when determinable), ranked hotels and per-hotel explanations.
- `POST /api/v1/journeys/{journeyId}/hotel-recommendations/run` reruns the evaluation and **persists** the result with a safe refresh strategy (created / updated / expired / unchanged counters).
- `POST /api/v1/hotels`, `GET /api/v1/hotels`, `GET /api/v1/hotels/{hotelId}` manage hotel master data (development/admin use).
- A dedicated `HotelRecommendationService` that decides between `OVERNIGHT_STAY_LIKELY`, `SAME_DAY_TRAVEL_FEASIBLE` and `INSUFFICIENT_TRAVEL_DATA`, plus a `HotelScoringService` that ranks and explains.
- A development-only, property-gated `HotelDemoDataSeeder` inserting a small set of clearly **fictional** demo hotels.

**Key design decisions**

| # | Decision | Rationale |
| :-- | :--- | :--- |
| D1 | Only `ACTIVE` journey impacts are considered | A resolved disruption must not produce hotel suggestions |
| D2 | No active impact → `SAME_DAY_TRAVEL_FEASIBLE` | Nothing has gone wrong that could strand the passenger |
| D3 | Active impacts that are **all journey-level** (no leg) → `INSUFFICIENT_TRAVEL_DATA` | A journey-level event names no leg, so the affected station cannot be determined |
| D4 | `CANCELLATION` / `MISSED_CONNECTION` forces an overnight **unless** Phase 6 has an `AVAILABLE` alternative-train recommendation for the same impact | If a feasible same-day alternative exists, the passenger is not stranded |
| D5 | A plain `DELAY` forces an overnight only when the delayed arrival **crosses midnight** or the delay reaches `overnight-delay-threshold-minutes` | An ordinary delay must not automatically trigger a hotel |
| D6 | Missing delay minutes or a missing arrival time → the impact is **undeterminable**, and the whole evaluation becomes `INSUFFICIENT_TRAVEL_DATA` | Times are never invented |
| D7 | The "stranded station" is the **source** station of a cancelled leg and the **destination** station of a missed connection / forcing delay | Matches where the passenger physically is |
| D8 | Hotels are ranked **currency-grouped** (ISO code ascending), then known-price, known-distance, distance ↑, price ↑, rating ↓, name, id | Prices in different currencies must never be compared without a real conversion source |
| D9 | Optional filters keep NULL-valued hotels (`maxPrice`/`minRating`/`maxDistanceKm` are "if known" limits); `currency` and `amenity` require an actual match | Real data must never be silently excluded because an optional field is missing |
| D10 | Persistence uses a derived `active_key` = `J<journeyId>:H<hotelId>`, `NULL` once `EXPIRED` | A nullable `UNIQUE` column gives one `AVAILABLE` row per (journey, hotel) plus unlimited history |
| D11 | Regeneration refreshes existing `AVAILABLE` rows and marks stale ones `EXPIRED` (key released) — never deletes | Idempotency and auditability |
| D12 | `HotelService.create` always stores `AVAILABILITY_UNKNOWN`; the client can never set an availability value | The system never stores a claim of confirmed availability |
| D13 | The seeder is a runtime `ApplicationRunner`, **not** a migration, and is property-gated | Fictional demo data must never be able to reach a production schema through Flyway |
| D14 | The decision is **stateless** (no `Clock`) | It uses stored relative impact/timetable values only, so it is reproducible and unit-testable |
| D15 | Endpoints are **unauthenticated development endpoints** | No authentication is implemented yet |

**Deviations from this phase's original brief (documented deliberately)**

- The GET endpoint is transient and the run endpoint persists. Hotel recommendations **are** persisted because the run endpoint is required to return created/updated/expired counters and because history is valuable; the design is documented in §5.
- The ranking is **currency-first** rather than purely price-first, to honour "do not compare prices in different currencies without a real conversion source". This is a deliberate choice, not an oversight.
- A `Clock` is **not** injected (unlike Phase 6). The overnight decision depends only on stored data, so a wall clock is unnecessary.

## 2. Files created / modified

**Created**

- [V9__hotel_recommendations.sql](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/db/migration/V9__hotel_recommendations.sql)
- [HotelAvailabilityStatus.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/HotelAvailabilityStatus.java) · [HotelDecisionOutcome.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/HotelDecisionOutcome.java) · [Hotel.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/Hotel.java) · [HotelRecommendation.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/HotelRecommendation.java)
- [HotelRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/HotelRepository.java) · [HotelRecommendationRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/HotelRecommendationRepository.java)
- [HotelService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/HotelService.java) · [HotelScoringService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/HotelScoringService.java) · [HotelRecommendationService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/HotelRecommendationService.java) · [HotelRecommendationResult.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/HotelRecommendationResult.java) · [HotelRecommendationRunResult.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/HotelRecommendationRunResult.java)
- [HotelRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/HotelRequest.java) · [HotelResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/HotelResponse.java) · [HotelRecommendationItem.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/HotelRecommendationItem.java) · [HotelRecommendationResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/HotelRecommendationResponse.java) · [HotelRecommendationRunResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/HotelRecommendationRunResponse.java)
- [HotelController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/HotelController.java)
- [HotelDemoDataSeeder.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/seed/HotelDemoDataSeeder.java)
- [HotelApiIntegrationTest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/test/java/com/trainconcierge/hotel/HotelApiIntegrationTest.java) · [HotelScoringServiceTest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/test/java/com/trainconcierge/service/HotelScoringServiceTest.java)
- `phase7.md` (this file)

**Modified (additive only)**

- [TrainConciergeProperties.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/TrainConciergeProperties.java) — added the nested `Hotel` block + `getHotel()`.
- [application.yml](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/application.yml) — added the `trainconcierge.hotel` block.

No Phase 0–6 behaviour was changed, no existing entity was altered, and no applied migration (`V1`–`V8`) was edited. `HotelRepository.search(...)` uses `left join fetch h.station` so a hotel's station can be serialised without an N+1 lazy load.

## 3. Database changes and migration instructions

New migration: `V9__hotel_recommendations.sql` (`V1`–`V8` untouched). Flyway applies it automatically at startup; there is nothing to run by hand. The migration is **additive only** and MySQL 8-compatible (`utf8mb4`, `InnoDB`, `DECIMAL` monetary columns).

**Important:** the migration contains **no `INSERT` of hotel rows**. Demo hotels are seeded at runtime by the property-gated seeder (§6), so fictional data can never reach a production database through a migration.

Table `hotels`:

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | `BIGINT AUTO_INCREMENT` | PK |
| `name` | `VARCHAR(150) NOT NULL` | |
| `station_id` | `BIGINT NULL` | FK → `stations(id)` `ON DELETE SET NULL`; a hotel may be described by a locality only |
| `locality` | `VARCHAR(150) NULL` | |
| `distance_km` | `DECIMAL(6,2) NULL` | never derived or fabricated |
| `price_per_night` | `DECIMAL(10,2) NULL` | |
| `currency` | `VARCHAR(3) NULL` | ISO code |
| `rating` | `DECIMAL(2,1) NULL` | 0–5 |
| `availability_status` | `VARCHAR(30) NOT NULL DEFAULT 'AVAILABILITY_UNKNOWN'` | `DEMO_DATA` / `AVAILABILITY_UNKNOWN` (deliberately no `CONFIRMED`) |
| `amenities` | `VARCHAR(255) NULL` | comma-separated |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` | |
| `created_at` / `updated_at` | `DATETIME(6)` | `CURRENT_TIMESTAMP(6)` defaults |

Indexes: `idx_hotels_name`, `idx_hotels_station`, `idx_hotels_currency`, `idx_hotels_price`, `idx_hotels_rating`, `idx_hotels_active`.

Table `hotel_recommendations`:

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | `BIGINT AUTO_INCREMENT` | PK |
| `journey_id` | `BIGINT NOT NULL` | FK → `journeys(id)` `ON DELETE CASCADE` |
| `journey_impact_id` | `BIGINT NULL` | FK → `journey_impacts(id)` `ON DELETE CASCADE`; the forcing impact |
| `hotel_id` | `BIGINT NOT NULL` | FK → `hotels(id)` `ON DELETE CASCADE` |
| `decision_outcome` | `VARCHAR(30) NOT NULL` | `OVERNIGHT_STAY_LIKELY` / `SAME_DAY_TRAVEL_FEASIBLE` / `INSUFFICIENT_TRAVEL_DATA` |
| `relevant_station_id` | `BIGINT NULL` | FK → `stations(id)` `ON DELETE SET NULL` |
| `rank_position` | `INT NOT NULL` | 1-based deterministic rank |
| `reason` | `VARCHAR(500) NOT NULL` | human-readable justification |
| `recommendation_status` | `VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'` | `AVAILABLE` / `EXPIRED` (produced) |
| `active_key` | `VARCHAR(190) NULL` | derived; `NULL` once `EXPIRED` |
| `created_at` / `updated_at` | `DATETIME(6)` | managed by Hibernate |

Constraints / indexes: `uk_hotel_recommendations_active_key UNIQUE (active_key)`, the four FKs, and indexes on `journey_id`, `journey_impact_id`, `hotel_id`, `recommendation_status`, and `(journey_id, recommendation_status)`.

**Why `active_key`**: MySQL permits many `NULL` values in a `UNIQUE` index, so exactly one `AVAILABLE` row can exist per `(journey, hotel)` while an unlimited, append-only history of previously suggested hotels is kept. When a suggestion is no longer valid it is set to `EXPIRED` and its key is released.

## 4. Hotel entity fields and validation rules

`Hotel` (`com.trainconcierge.entity.Hotel`, table `hotels`) — `id`, `name` (150, not null), `station` (`@ManyToOne(LAZY)`, nullable), `locality` (150), `distanceKm` (6,2), `pricePerNight` (10,2), `currency` (3), `rating` (2,1), `availabilityStatus` (`@Enumerated(STRING)`, default `AVAILABILITY_UNKNOWN`), `amenities` (255), `active` (default `true`), `createdAt`, `updatedAt`. `BigDecimal` is used for `distanceKm`, `pricePerNight` and `rating`.

`HotelRequest` validation (Jakarta Validation, enforced on `POST /api/v1/hotels`):

| Field | Rule | Message |
| :--- | :--- | :--- |
| `name` | `@NotBlank`, `@Size(max = 150)` | `name must not be blank` / `name must be at most 150 characters` |
| `stationId` | optional; `404` if it does not exist | `Station not found with id: …` |
| `locality` | `@Size(max = 150)` | `locality must be at most 150 characters` |
| `distanceKm` | `@DecimalMin("0.0")`, `@DecimalMax("1000.0")` | `distanceKm must be zero or positive` / `distanceKm must be at most 1000` |
| `pricePerNight` | `@DecimalMin("0.01")`, `@DecimalMax("1000000.00")` | `pricePerNight must be greater than zero` / `pricePerNight is unrealistically large` |
| `currency` | `@Pattern("^[A-Za-z]{3}$")` | `currency must be a 3-letter currency code` |
| `rating` | `@DecimalMin("0.0")`, `@DecimalMax("5.0")` | `rating must be zero or positive` / `rating must be at most 5` |
| `amenities` | `@Size(max = 255)` | `amenities must be at most 255 characters` |
| `active` | optional `Boolean`; defaults to `true` | — |

Serverside normalisation: `name` is trimmed; `currency` is trimmed and upper-cased; `amenities` are trimmed, upper-cased, de-duplicated and comma-joined; blank strings become `null`. The **availability status is never accepted from the client** — a created hotel is always stored as `AVAILABILITY_UNKNOWN`.

## 5. When an overnight stay is likely, and how hotels are ranked

### 5.1 The decision (`HotelRecommendationService`)

Applied in order; the first matching rule decides:

1. Only `ACTIVE` journey impacts of the journey are considered.
2. **No active impact** → `SAME_DAY_TRAVEL_FEASIBLE` — "No active journey impact requires an overnight stay."
3. Active impacts that are **all journey-level** (no leg) → `INSUFFICIENT_TRAVEL_DATA`.
4. For each leg-specific impact, `assessLeg` decides whether it *forces* an overnight:
   - `CANCELLATION` → stranded at the leg's **source** station; forces an overnight **unless** `TrainRecommendationRepository.findActiveForImpact(impactId, AVAILABLE)` is non-empty.
   - `MISSED_CONNECTION` → stranded at the leg's **destination** station; same feasibility check.
   - `DELAY` → needs `delayMinutes` and the leg's `arrivalTime`; forces an overnight when `arrivalMinutes + delay >= 1440` (crosses midnight) **or** `delay >= trainconcierge.hotel.overnight-delay-threshold-minutes`. Missing either value makes it *undeterminable*.
   - `ITINERARY_DISRUPTED` → undeterminable.
5. If **any** impact forces an overnight → `OVERNIGHT_STAY_LIKELY` at that impact's stranded station. The forcing impact is chosen deterministically: leg sequence ascending, then severity descending, then impact id ascending.
6. Otherwise, if any leg-specific impact was **undeterminable** → `INSUFFICIENT_TRAVEL_DATA`; else `SAME_DAY_TRAVEL_FEASIBLE`.

If there is a feasible same-day alternative (Phase 6), the passenger is **not** classified as needing a hotel.

**Limitations of a timetable-based decision (honest disclosure)**

- The decision is only as good as the stored `journey_legs`, `journey_impacts`, `disruption_events` and `train_recommendations` data. Train running statuses are **simulated** (Phase 3), not live.
- There is no wall clock: "until the next service period" cannot be evaluated from absolute time, so it is approximated by "the delayed arrival crosses midnight" and a configurable delay threshold.
- It never asserts that the passenger *cannot* travel; it only says a hotel is *likely* or that travel is *feasible*. When data is missing it says so (`INSUFFICIENT_TRAVEL_DATA`) instead of guessing.
- No distance, price, rating or availability is ever invented.

### 5.2 Hotel ranking (`HotelScoringService`, lower position = better)

1. Hotels with a **known price** before hotels without one.
2. Grouped by **currency** (ISO code ascending, unknown last) — cross-currency price comparison is never performed.
3. Within a currency group, **known distance** first, then **increasing distance** (closest first).
4. Then **increasing price** (only ever inside the same currency group).
5. Then **decreasing rating**.
6. Finally **name**, then **id** — a stable tie-breaker.

Missing optional values never remove a hotel; they only move it later in the order.

### 5.3 Filters

Applied by `HotelRepository.search(...)`: `maxPrice`, `currency`, `minRating`, `maxDistanceKm`, `amenity`. `maxPrice`, `minRating` and `maxDistanceKm` keep hotels whose value is `NULL` (an "if known" limit); `currency` and `amenity` require a real match. The result limit is `trainconcierge.hotel.max-recommendations`; the master-data list endpoint supports `page`/`size`/`sort`.

### 5.4 Explanations

Every recommendation carries a `reason` derived from stored data, e.g. `Recommended because it is associated with … (CSMT), the station where the journey is disrupted; 0.8 km from the station; INR 4500 per night (within your limit of 5000); rating 4.2; seeded demo data - availability is not confirmed and this is not a booking; locality: Fort, near CSMT.` Missing distance/price/rating are stated explicitly ("distance from the station is not recorded", …). `reason` is truncated to the stored column width (500).

## 6. Demo hotel seeding

`HotelDemoDataSeeder` inserts **eight fictional demo hotels** for development and Postman testing. Every name ends with `(fictional)` and every row is stored with `availabilityStatus = DEMO_DATA`. Prices, distances and ratings are realistic-looking but clearly illustrative — they are **not real businesses** and no availability is claimed.

| Name | Station | Distance (km) | Price/night | Rating | Amenities |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Demo Grand Mumbai Central (fictional) | CSMT | 0.80 | 4500.00 | 4.2 | WIFI,BREAKFAST,AIRPORT_TRANSFER |
| Demo Budget Stay Mumbai (fictional) | CSMT | 1.90 | 1200.00 | 3.1 | WIFI |
| Demo Heritage Residency (fictional) | CSMT | *(unknown)* | *(unknown)* | *(unknown)* | *(none)* |
| Demo Capital Inn Delhi (fictional) | NDLS | 1.20 | 3000.00 | 3.9 | WIFI,BREAKFAST |
| Demo Metro Suites Delhi (fictional) | NDLS | 6.50 | 7200.00 | 4.6 | WIFI,POOL,GYM |
| Demo Deccan Comfort Pune (fictional) | PUNE | 0.60 | 2600.00 | 4.0 | WIFI,BREAKFAST |
| Demo Koregaon Boutique Pune (fictional) | PUNE | 5.40 | 5400.00 | 4.4 | WIFI,SPA |
| Demo Solapur Transit Lodge (fictional) | SUR | 0.40 | 900.00 | 2.8 | *(none)* |

The stations `CSMT`, `NDLS`, `PUNE`, `SUR` come from `V3__seed_railway_sample_data.sql`. If a referenced station is absent, that hotel is **skipped** (logged as a warning) rather than given a fabricated location. "Demo Heritage Residency (fictional)" deliberately has no price, distance, rating, currency or amenities to demonstrate that a hotel with unknown optional data is still recommended (with the gaps stated). Currency is set to `INR` only when a price exists.

**Repeatability**: each hotel is inserted only when a hotel with the same name is not already linked to the same station, so restarts never create duplicates.

**How to disable**: set `trainconcierge.hotel.demo-seed-enabled=false` (`TRAINCONCIERGE_HOTEL_DEMO_SEED_ENABLED=false`) in production. The seeder is `@ConditionalOnProperty(prefix = "trainconcierge.hotel", name = "demo-seed-enabled", havingValue = "true", matchIfMissing = true)`, so it is simply not created when disabled.

## 7. Configuration and startup

| Property | Env variable | Default | Meaning |
| :--- | :--- | :--- | :--- |
| `trainconcierge.hotel.demo-seed-enabled` | `TRAINCONCIERGE_HOTEL_DEMO_SEED_ENABLED` | `true` | Seed the fictional demo hotels at startup (development only) |
| `trainconcierge.hotel.overnight-delay-threshold-minutes` | `TRAINCONCIERGE_HOTEL_OVERNIGHT_DELAY_THRESHOLD_MINUTES` | `480` | A delay at or above this many minutes counts as overnight-forcing |
| `trainconcierge.hotel.max-recommendations` | `TRAINCONCIERGE_HOTEL_MAX_RECOMMENDATIONS` | `10` | Maximum hotels returned/kept per journey evaluation |

`trainconcierge.monitoring.*`, `disruption.*`, `impact.*` and `recommendation.*` are unchanged.

```powershell
# 1. MySQL 8.x running, database trainconcierge_db exists (Phase 0)
$env:DB_USERNAME="root"; $env:DB_PASSWORD="P@t!3nc3"

# 2. Start the app - Flyway applies V9 automatically, then the demo seeder runs
mvn spring-boot:run

# 3. Run tests / build
mvn test
mvn clean package
```

Expected on startup: `Successfully validated 9 migrations`, `Current version of schema trainconcierge_db: 9`, and a seeder log line `Demo hotel seeding complete: 8 created, 0 already present (fictional demo data; …)`. The manual trigger endpoints work even with `trainconcierge.monitoring.enabled=false`.

## 8. API reference (Postman)

Base URL: `http://localhost:8080/api/v1` · Header: `Content-Type: application/json` (where a body is sent).
**These are unauthenticated development endpoints.**

| # | Method | URL | Purpose |
| :-- | :--- | :--- | :--- |
| 1 | POST | `/hotels` | Create a hotel (dev/admin); always stored `AVAILABILITY_UNKNOWN`; `201` with a `Location` header |
| 2 | GET | `/hotels?name=&stationId=&currency=&active=&maxPrice=&minRating=&maxDistanceKm=&amenity=&page=&size=&sort=` | Paginated hotel search |
| 3 | GET | `/hotels/{hotelId}` | One hotel · `404` if absent |
| 4 | GET | `/journeys/{journeyId}/hotel-recommendations?maxPrice=&currency=&minRating=&maxDistanceKm=&amenity=` | Transient evaluation + ranked hotels · `404` if the journey is absent |
| 5 | POST | `/journeys/{journeyId}/hotel-recommendations/run?…same filters…` | Rerun and persist; returns created/updated/expired/unchanged counters · `404` if the journey is absent |

Hotel list defaults: `page=0`, `size=20`, `sort=id,asc`. An unknown `sort` property → `400`.

### 1. POST `/hotels`

Request:
```json
{ "name": "Demo Riverside Lodge (fictional)", "stationId": 1, "locality": "Near CSMT",
  "distanceKm": 1.50, "pricePerNight": 2500.00, "currency": "inr", "rating": 3.7,
  "amenities": "wifi, breakfast", "active": true }
```
`201 Created` (`Location: /api/v1/hotels/9`):
```json
{
  "id": 9, "name": "Demo Riverside Lodge (fictional)",
  "station": { "id": 1, "stationCode": "CSMT", "name": "Chhatrapati Shivaji Maharaj Terminus", "city": "Mumbai" },
  "locality": "Near CSMT", "distanceKm": 1.50, "pricePerNight": 2500.00, "currency": "INR",
  "rating": 3.7, "availabilityStatus": "AVAILABILITY_UNKNOWN",
  "amenities": ["WIFI", "BREAKFAST"], "active": true,
  "createdAt": "2026-10-26T01:20:00.000+05:30", "updatedAt": "2026-10-26T01:20:00.000+05:30"
}
```
Note `currency` was upper-cased to `INR` and `amenities` normalised to `["WIFI","BREAKFAST"]`.

Invalid body → `400`:
```json
{ "timestamp": "2026-10-26T01:20:00+05:30", "status": 400, "error": "Bad Request",
  "message": "Validation failed", "path": "/api/v1/hotels",
  "fieldErrors": { "pricePerNight": "pricePerNight must be greater than zero" } }
```

### 2. GET `/hotels`

`200 OK`:
```json
{
  "content": [
    { "id": 1, "name": "Demo Grand Mumbai Central (fictional)",
      "station": { "id": 1, "stationCode": "CSMT", "name": "Chhatrapati Shivaji Maharaj Terminus", "city": "Mumbai" },
      "locality": "Fort, near CSMT", "distanceKm": 0.80, "pricePerNight": 4500.00, "currency": "INR",
      "rating": 4.2, "availabilityStatus": "DEMO_DATA",
      "amenities": ["WIFI", "BREAKFAST", "AIRPORT_TRANSFER"], "active": true,
      "createdAt": "2026-10-26T01:10:00.000+05:30", "updatedAt": "2026-10-26T01:10:00.000+05:30" }
  ],
  "page": 0, "size": 20, "totalElements": 8, "totalPages": 1, "first": true, "last": true
}
```

### 3. GET `/hotels/{hotelId}`

Missing id → `404`:
```json
{ "timestamp": "2026-10-26T01:20:00+05:30", "status": 404, "error": "Not Found",
  "message": "Hotel not found with id: 999999", "path": "/api/v1/hotels/999999" }
```

### 4. GET `/journeys/{journeyId}/hotel-recommendations`

`200 OK` — overnight likely (cancelled leg, no feasible alternative):
```json
{
  "journeyId": 1, "journeyReference": "TC-1A2B3C4D",
  "decisionOutcome": "OVERNIGHT_STAY_LIKELY", "overnightStayLikely": true,
  "relevantStation": { "id": 1, "stationCode": "CSMT", "name": "Chhatrapati Shivaji Maharaj Terminus", "city": "Mumbai" },
  "decisionReason": "Train 12123 on leg 1 (CSMT -> PUNE) is cancelled for the service date and no feasible same-day alternative was found, so the passenger is likely stranded at Chhatrapati Shivaji Maharaj Terminus (CSMT).",
  "recommendations": [
    { "rank": 1,
      "hotel": { "id": 1, "name": "Demo Grand Mumbai Central (fictional)",
        "station": { "id": 1, "stationCode": "CSMT", "name": "Chhatrapati Shivaji Maharaj Terminus", "city": "Mumbai" },
        "locality": "Fort, near CSMT", "distanceKm": 0.80, "pricePerNight": 4500.00, "currency": "INR",
        "rating": 4.2, "availabilityStatus": "DEMO_DATA",
        "amenities": ["WIFI", "BREAKFAST", "AIRPORT_TRANSFER"], "active": true,
        "createdAt": "2026-10-26T01:10:00.000+05:30", "updatedAt": "2026-10-26T01:10:00.000+05:30" },
      "reason": "Recommended because it is associated with Chhatrapati Shivaji Maharaj Terminus (CSMT), the station where the journey is disrupted; 0.8 km from the station; INR 4500 per night; rating 4.2; seeded demo data - availability is not confirmed and this is not a booking; locality: Fort, near CSMT." }
  ]
}
```

`200 OK` — same-day travel feasible:
```json
{
  "journeyId": 2, "journeyReference": "TC-9F8E7D6C",
  "decisionOutcome": "SAME_DAY_TRAVEL_FEASIBLE", "overnightStayLikely": false,
  "relevantStation": null,
  "decisionReason": "A same-day continuation of the journey remains possible, so no overnight stay is likely.",
  "recommendations": []
}
```

`200 OK` — insufficient data:
```json
{
  "journeyId": 3, "journeyReference": "TC-11223344",
  "decisionOutcome": "INSUFFICIENT_TRAVEL_DATA", "overnightStayLikely": false,
  "relevantStation": null,
  "decisionReason": "The stored timetable or impact data is not sufficient to decide whether an overnight stay is needed.",
  "recommendations": []
}
```

Missing journey → `404`: `{ … "message": "Journey not found with id: 999999", "path": "/api/v1/journeys/999999/hotel-recommendations" }`.

### 5. POST `/journeys/{journeyId}/hotel-recommendations/run`

`200 OK` (first run, two hotels):
```json
{ "journeyId": 1, "decisionOutcome": "OVERNIGHT_STAY_LIKELY", "overnightStayLikely": true,
  "relevantStationId": 1, "recommendationsCreated": 2, "recommendationsUpdated": 0,
  "recommendationsExpired": 0, "recommendationsUnchanged": 0, "recommendationsReturned": 2,
  "reason": "Train 12123 on leg 1 (CSMT -> PUNE) is cancelled for the service date and no feasible same-day alternative was found, so the passenger is likely stranded at Chhatrapati Shivaji Maharaj Terminus (CSMT)." }
```

`200 OK` (second run, no data change — idempotent):
```json
{ "journeyId": 1, "decisionOutcome": "OVERNIGHT_STAY_LIKELY", "overnightStayLikely": true,
  "relevantStationId": 1, "recommendationsCreated": 0, "recommendationsUpdated": 0,
  "recommendationsExpired": 0, "recommendationsUnchanged": 2, "recommendationsReturned": 2,
  "reason": "Train 12123 on leg 1 (CSMT -> PUNE) is cancelled for the service date and no feasible same-day alternative was found, so the passenger is likely stranded at Chhatrapati Shivaji Maharaj Terminus (CSMT)." }
```

`200 OK` (same-day feasible — any previous rows are expired):
```json
{ "journeyId": 1, "decisionOutcome": "SAME_DAY_TRAVEL_FEASIBLE", "overnightStayLikely": false,
  "relevantStationId": null, "recommendationsCreated": 0, "recommendationsUpdated": 0,
  "recommendationsExpired": 2, "recommendationsUnchanged": 0, "recommendationsReturned": 0,
  "reason": "A same-day continuation of the journey remains possible, so no overnight stay is likely." }
```

## 9. End-to-end Postman workflow

This reuses **only** endpoints implemented in Phases 1–6. The journey date must be inside the Phase 5 analysis window (`[today − 1, today + 7]`), so it uses `today + 2`.

```powershell
$base = "http://localhost:8080/api/v1"
$date = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
```

### Workflow A — cancellation with no same-day alternative → overnight stay likely

**A1. Master data** — two stations, the disrupted train, and its timetable.
```powershell
$s1 = (Invoke-RestMethod -Method Post "$base/stations" -ContentType "application/json" `
        -Body (@{ stationCode="P7A"; name="Phase7 Station A"; city="Demo"; state="Demo" } | ConvertTo-Json)).id
$s2 = (Invoke-RestMethod -Method Post "$base/stations" -ContentType "application/json" `
        -Body (@{ stationCode="P7B"; name="Phase7 Station B"; city="Demo"; state="Demo" } | ConvertTo-Json)).id

$broken = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="990101"; name="Phase7 Disrupted"; trainType="EXPRESS";
                  sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id

Invoke-RestMethod -Method Post "$base/trains/$broken/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="08:00:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$broken/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="10:00:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)
```

**A2. A hotel near the stranded (source) station**
```powershell
$hotel = (Invoke-RestMethod -Method Post "$base/hotels" -ContentType "application/json" `
        -Body (@{ name="Phase7 Demo Inn A (fictional)"; stationId=$s1; locality="Phase7 A centre";
                  distanceKm=1.20; pricePerNight=2500.00; currency="INR"; rating=4.1;
                  amenities="WIFI,BREAKFAST"; active=$true } | ConvertTo-Json)).id
```

**A3. Passenger, journey, cancellation, pipeline**
```powershell
$pax = (Invoke-RestMethod -Method Post "$base/passengers" -ContentType "application/json" `
        -Body (@{ fullName="Phase7 Passenger"; email="phase7a@example.com" } | ConvertTo-Json)).id

$journey = (Invoke-RestMethod -Method Post "$base/journeys" -ContentType "application/json" `
        -Body (@{ passengerId=$pax; journeyDate=$date; legs=@(
                   @{ trainId=$broken; sourceStationId=$s1; destinationStationId=$s2; legSequence=1 }) } `
               | ConvertTo-Json -Depth 5)).id

Invoke-RestMethod -Method Post "$base/trains/$broken/status/cancel" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; reason="Phase7 demo cancellation" } | ConvertTo-Json)

Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json        # Phase 4: eventsCreated = 1
Invoke-RestMethod -Method Post "$base/impact-analysis/run" | ConvertTo-Json   # Phase 5: impacts for this journey
# No alternative train exists, so Phase 6 has nothing to offer for the impact.
```

**A4. Evaluate hotel recommendations**
```powershell
Invoke-RestMethod "$base/journeys/$journey/hotel-recommendations" | ConvertTo-Json -Depth 8
# expect decisionOutcome = "OVERNIGHT_STAY_LIKELY", relevantStation.id = $s1,
#        one recommendation = "Phase7 Demo Inn A (fictional)", rank = 1,
#        every reason ends with "…not a booking"

Invoke-RestMethod -Method Post "$base/journeys/$journey/hotel-recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsCreated = 1
Invoke-RestMethod -Method Post "$base/journeys/$journey/hotel-recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsCreated = 0, recommendationsUnchanged = 1
```

### Workflow B — cancellation with a feasible alternative → same-day travel feasible

Add an alternative train serving the same origin → destination and mark it on time, then let Phase 6 produce an `AVAILABLE` recommendation for the impact; the hotel evaluation must then return `SAME_DAY_TRAVEL_FEASIBLE`.

```powershell
$alt = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="990102"; name="Phase7 Alternative"; trainType="EXPRESS";
                  sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id
Invoke-RestMethod -Method Post "$base/trains/$alt/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="11:00:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$alt/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime="13:00:00"; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)

# Phase 6: generate the alternative-train recommendation for the cancellation impact
$impact = (Invoke-RestMethod "$base/journeys/$journey/impacts").content `
          | Where-Object { $_.impactType -eq "CANCELLATION" } | Select-Object -First 1
Invoke-RestMethod -Method Post "$base/impacts/$($impact.id)/recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsReturned = 1

Invoke-RestMethod "$base/journeys/$journey/hotel-recommendations" | ConvertTo-Json -Depth 8
# expect decisionOutcome = "SAME_DAY_TRAVEL_FEASIBLE", overnightStayLikely = false, recommendations = []

Invoke-RestMethod -Method Post "$base/journeys/$journey/hotel-recommendations/run" | ConvertTo-Json -Depth 5
# expect recommendationsExpired = 1 (the earlier hotel row is retired), recommendationsReturned = 0
```

### Workflow C — insufficient timetable data

A `DELAY` impact whose `delayMinutes` is `null`, or a leg whose `arrivalTime` is `null`, cannot be evaluated. The evaluation then returns `INSUFFICIENT_TRAVEL_DATA` rather than asserting an overnight stay or safe same-day travel.

```powershell
# delay the disrupted train's leg but leave the leg arrival time unknown on a fresh journey:
$brokenC = (Invoke-RestMethod -Method Post "$base/trains" -ContentType "application/json" `
        -Body (@{ trainNumber="990103"; name="Phase7 NoArrival"; trainType="EXPRESS";
                  sourceStationId=$s1; destinationStationId=$s2 } | ConvertTo-Json)).id
Invoke-RestMethod -Method Post "$base/trains/$brokenC/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s1; stopSequence=1; arrivalTime=$null; departureTime="08:00:00"; dayOffset=0; distanceFromSourceKm=0 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/trains/$brokenC/stops" -ContentType "application/json" `
  -Body (@{ stationId=$s2; stopSequence=2; arrivalTime=$null; departureTime=$null; dayOffset=0; distanceFromSourceKm=100 } | ConvertTo-Json)

$paxC = (Invoke-RestMethod -Method Post "$base/passengers" -ContentType "application/json" `
        -Body (@{ fullName="Phase7 Insufficient"; email="phase7c@example.com" } | ConvertTo-Json)).id
$journeyC = (Invoke-RestMethod -Method Post "$base/journeys" -ContentType "application/json" `
        -Body (@{ passengerId=$paxC; journeyDate=$date; legs=@(
                   @{ trainId=$brokenC; sourceStationId=$s1; destinationStationId=$s2; legSequence=1 }) } `
               | ConvertTo-Json -Depth 5)).id

Invoke-RestMethod -Method Post "$base/trains/$brokenC/status/delay" -ContentType "application/json" `
  -Body (@{ serviceDate=$date; delayMinutes=45; reason="Phase7 demo delay" } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$base/monitoring/run" | ConvertTo-Json
Invoke-RestMethod -Method Post "$base/impact-analysis/run" | ConvertTo-Json

Invoke-RestMethod "$base/journeys/$journeyC/hotel-recommendations" | ConvertTo-Json -Depth 8
# expect decisionOutcome = "INSUFFICIENT_TRAVEL_DATA", recommendations = []
```

## 10. Automated tests and build results

Commands and honest results (run against the local MySQL 8.0.46 instance, which was available):

```powershell
mvn test "-Dtest=HotelScoringServiceTest,HotelApiIntegrationTest" "-DfailIfNoTests=false"   # Phase 7 only
mvn test                                                                                     # full suite
```

```
Phase 7 integration class:  Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
Phase 7 scoring unit class: Tests run:  8, Failures: 0, Errors: 0, Skipped: 0
Full suite:                 Tests run: 146, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Flyway: Successfully validated 9 migrations - schema version 9
```

The full suite grew from **121** (Phase 6) to **146** tests; Phases 0–6 are unaffected.

`HotelApiIntegrationTest` (17 tests, `@SpringBootTest` + MockMvc, `@Transactional`, scheduled monitoring disabled, `max-recommendations=5`) covers:

- a cancelled leg with no feasible alternative → `OVERNIGHT_STAY_LIKELY`, the origin hotel recommended, `availabilityStatus = AVAILABILITY_UNKNOWN`, reason contains "not a booking"/"per night"/"km from the station";
- a cancellation **with** an `AVAILABLE` Phase 6 alternative → `SAME_DAY_TRAVEL_FEASIBLE`, empty recommendations;
- a missed connection on a two-leg journey → overnight likely at the **transfer** station (leg 1 destination);
- an ordinary 60-minute delay with a 10:00 arrival → `SAME_DAY_TRAVEL_FEASIBLE`;
- a 60-minute delay on a 23:30 arrival (crosses midnight) → `OVERNIGHT_STAY_LIKELY`;
- no active impact → `SAME_DAY_TRAVEL_FEASIBLE`, `relevantStation` absent;
- a journey-level `ITINERARY_DISRUPTED` → `INSUFFICIENT_TRAVEL_DATA`;
- a `DELAY` with `delayMinutes == null` → `INSUFFICIENT_TRAVEL_DATA` with a non-empty reason;
- every hotel search filter (`maxPrice` keeps cheap *and* unknown-price hotels; `maxDistanceKm`; `minRating`; `active=false`; `currency`; `amenity`) and computed `HotelResponse` fields;
- inactive hotels never being recommended;
- deterministic ranking and tie-breaking (0.50 km first; same 1.00 km → cheaper first; ranks 1/2/3 asserted);
- invalid hotel payloads (negative price, negative distance, rating 9.9, blank name, `RUPEE` currency) → `400`;
- unknown hotel/journey ids → `404`;
- repeated runs (create 2 → unchanged 2 → after adding an alternative, expire 2 and return 0) with `EXPIRED` history preserved and `activeKey == null`;
- the demo seed being repeatable (count unchanged, every `DEMO_DATA` name contains "fictional");
- an empty search returning an empty page rather than an error;
- a Phase 0–6 endpoint regression check (stations, trains, journeys, disruptions, impacts, `POST /recommendations/run`, hotels all `200`).

`HotelScoringServiceTest` (8 plain-JUnit tests, no Spring/DB) covers known-price-first + currency grouping, distance → price → rating → name ordering, unknown distance last but not removed, name/id tie-break, the explanation for known data, the explanation for missing data, a price-limit echo, and `truncate` capping at `MAX_REASON_LENGTH`.

No external service is contacted and every integration test rolls back.

## 11. Troubleshooting

- **`decisionOutcome: "SAME_DAY_TRAVEL_FEASIBLE"` when a hotel was expected**: either there is no `ACTIVE` impact, or Phase 6 produced an `AVAILABLE` alternative for the impact (a feasible same-day alternative exists). Check `GET /journeys/{id}/recommendations`.
- **`decisionOutcome: "INSUFFICIENT_TRAVEL_DATA"`**: the active impact is journey-level, or a `DELAY`/leg is missing `delayMinutes`/`arrivalTime`. Times are never invented; fix the underlying impact or timetable data.
- **`recommendationsCreated` stays `0` while `recommendationsReturned` is `> 0`**: expected — the rows already exist and were confirmed unchanged. Change the disruption/alternative data and rerun to see `updated`/`expired`.
- **`OVERNIGHT_STAY_LIKELY` but `recommendations` is empty**: no **active** hotel exists near the stranded station, or the supplied filters excluded every candidate. Widen the filters or create an active hotel for that station.
- **A demo hotel is missing**: its station code (`CSMT`/`NDLS`/`PUNE`/`SUR`) is absent from `stations` — the seeder skips it with a warning rather than inventing a location. Re-run the app after `V3` has seeded the stations.
- **`400 Bad Request`**: a validation failure on `POST /hotels` (negative price/distance, rating > 5, bad currency, blank name), an unparseable numeric query parameter, or an unknown `sort` property.
- **`409 Conflict`**: a `hotel_recommendations.active_key` unique violation — normally only reachable through concurrent runs; rerun the evaluation.
- **`404 Not Found`**: unknown `journeyId`, `hotelId` or `stationId`.
- **Flyway failure**: check `flyway_schema_history`; never edit an applied migration — add a new one.
- **Scheduler running during tests**: set `trainconcierge.monitoring.enabled=false` (`TRAINCONCIERGE_MONITORING_ENABLED=false`).
- **MySQL connection errors**: verify `DB_USERNAME`/`DB_PASSWORD`/`DB_NAME` and that MySQL is on `localhost:3306`.
- **`ENOTFOUND` in Postman**: prefix local requests with `http://localhost:8080`.

## 12. Git review and commit

Review first and confirm no secrets (`.env`, `application-local.yml`, credentials) are staged.

```powershell
git status
git diff
git add src/main/resources/db/migration/V9__hotel_recommendations.sql `
        src/main/java/com/trainconcierge/entity/HotelAvailabilityStatus.java `
        src/main/java/com/trainconcierge/entity/HotelDecisionOutcome.java `
        src/main/java/com/trainconcierge/entity/Hotel.java `
        src/main/java/com/trainconcierge/entity/HotelRecommendation.java `
        src/main/java/com/trainconcierge/repository/HotelRepository.java `
        src/main/java/com/trainconcierge/repository/HotelRecommendationRepository.java `
        src/main/java/com/trainconcierge/service/HotelService.java `
        src/main/java/com/trainconcierge/service/HotelScoringService.java `
        src/main/java/com/trainconcierge/service/HotelRecommendationService.java `
        src/main/java/com/trainconcierge/service/HotelRecommendationResult.java `
        src/main/java/com/trainconcierge/service/HotelRecommendationRunResult.java `
        src/main/java/com/trainconcierge/dto/HotelRequest.java `
        src/main/java/com/trainconcierge/dto/HotelResponse.java `
        src/main/java/com/trainconcierge/dto/HotelRecommendationItem.java `
        src/main/java/com/trainconcierge/dto/HotelRecommendationResponse.java `
        src/main/java/com/trainconcierge/dto/HotelRecommendationRunResponse.java `
        src/main/java/com/trainconcierge/controller/HotelController.java `
        src/main/java/com/trainconcierge/seed/HotelDemoDataSeeder.java `
        src/main/java/com/trainconcierge/config/TrainConciergeProperties.java `
        src/main/resources/application.yml `
        src/test/java/com/trainconcierge/service/HotelScoringServiceTest.java `
        src/test/java/com/trainconcierge/hotel/HotelApiIntegrationTest.java `
        phase7.md
git status
git commit -m "Complete TrainConcierge Phase 7 hotel recommendations"
git push
```

If the branch has no upstream: `git push -u origin HEAD`.

## 13. Known limitations

- Train running statuses are **simulated** (Phase 3); a real disruption feed is out of scope.
- The overnight decision has no wall clock, so it approximates "no reasonable continuation until the next service period" with a midnight-crossing check and a configurable delay threshold.
- Hotel data is **fictional demo data**. No live availability, pricing or booking integration exists, and none is claimed.
- Prices in different currencies are never compared; results are grouped by currency.
- A true concurrent two-writer race is not reproducible deterministically in a JDBC test; the database guarantee it relies on (the `active_key` unique index) *is* exercised by the repeated-run tests.

## 14. Recommended commit message

`Complete TrainConcierge Phase 7 hotel recommendations`
