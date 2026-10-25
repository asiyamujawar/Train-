# TrainConcierge — Phase 2: Passenger Management and Journey Planning (MySQL)

## 1. Phase objective and implemented features

Phase 2 focuses on passenger record management and atomic journey planning. A journey consists of one or more "legs" (segments) which are validated against the train timetables established in Phase 1.

### Implemented features:
- **Passenger Management**:
  - Full CRUD operations for passengers.
  - Unique email enforcement with lowercase normalization.
  - Soft deactivation (active/inactive status).
  - Paginated search and filtering.
- **Journey Planning**:
  - Atomic journey creation: a journey and all its legs are saved in a single transaction.
  - Multi-leg itinerary validation:
    - Train and station existence and active status.
    - Segment validation: source must appear before destination in the train's timetable.
    - Continuity validation: the destination of leg $N$ must match the source of leg $N+1$.
    - Sequence validation: legs must be contiguous and start from 1.
  - Automatic time capture: planned departure and arrival times are pulled from the timetable stops.
  - Unique journey reference generation (e.g., `TC-A1B2C3D4`).
  - Journey cancellation (status update to `CANCELLED`).
  - Journey retrieval by ID or unique reference.
  - Paginated listing of a passenger's journeys.

## 2. Entity relationships and design decisions

### 2.1 Entity relationship model
- **Passenger**: One-to-many relationship with **Journey**.
- **Journey**: One-to-many relationship with **JourneyLeg** (Cascade ALL, Orphan Removal).
- **JourneyLeg**: Many-to-one relationships with **Train**, **Station** (source), and **Station** (destination).

### 2.2 Design decisions
- **Atomic Transactions**: All legs are validated and saved together. If one leg is invalid (e.g., disconnected stations), the entire journey creation fails and rolls back.
- **Lazy Loading**: All associations use `FetchType.LAZY` to prevent N+1 issues and accidental massive serializations. Repositories use `join fetch` or explicit queries where performance is critical.
- **Status Enum**: Journeys use a `JourneyStatus` enum (`PLANNED`, `ACTIVE`, `COMPLETED`, `CANCELLED`) for strict state management.
- **Soft Delete**: Passengers are deactivated rather than deleted to preserve their historical journey data.
- **Reference Generation**: Journey references are generated using a random UUID prefix to ensure uniqueness and readability without exposing internal IDs.

## 3. Files created and modified

### 3.1 Entities
- [Passenger.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/Passenger.java): Passenger record with email uniqueness.
- [Journey.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/Journey.java): Main journey record with legs.
- [JourneyLeg.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/JourneyLeg.java): A single segment of a journey.
- [JourneyStatus.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/entity/JourneyStatus.java): Enum for journey lifecycle.

### 3.2 Repositories
- [PassengerRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/PassengerRepository.java): Email lookup and search.
- [JourneyRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/JourneyRepository.java): Reference lookup and search.
- [JourneyLegRepository.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/repository/JourneyLegRepository.java): Leg retrieval.

### 3.3 Services
- [PassengerService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/PassengerService.java): Passenger business logic.
- [JourneyService.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/service/JourneyService.java): Complex journey validation and atomic creation.

### 3.4 Controllers
- [PassengerController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/PassengerController.java): REST endpoints for passengers.
- [JourneyController.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/controller/JourneyController.java): REST endpoints for journeys.

### 3.5 DTOs
- [PassengerRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/PassengerRequest.java) / [PassengerResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/PassengerResponse.java)
- [JourneyRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/JourneyRequest.java) / [JourneyResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/JourneyResponse.java)
- [JourneyLegRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/JourneyLegRequest.java) / [JourneyLegResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/JourneyLegResponse.java)
- [PassengerSummaryResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/PassengerSummaryResponse.java)

### 3.6 Configuration & Other
- [OpenApiConfig.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/OpenApiConfig.java): Updated description.
- [V4__passenger_journey_data.sql](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/db/migration/V4__passenger_journey_data.sql): Database schema for Phase 2.

## 4. Flyway migration and database changes

**Migration File**: `V4__passenger_journey_data.sql`

**Changes**:
- `passengers`: Stores profile data. Unique index on `email`.
- `journeys`: Stores root journey data. Foreign key to `passengers`. Unique index on `journey_reference`.
- `journey_legs`: Stores segments. Foreign keys to `journeys`, `trains`, and `stations`. Unique constraint on `(journey_id, leg_sequence)`.

## 5. MySQL setup requirements

Refer to **Phase 0** for initial MySQL installation and database creation (`trainconcierge_db`). Phase 2 requires no new database setup beyond running the Flyway migrations automatically on startup.

## 6. Startup instructions

1. Ensure MySQL is running.
2. Set environment variables:
   ```powershell
   $env:DB_USERNAME="root"
   $env:DB_PASSWORD="P@t!3nc3"
   ```
3. Run the application:
   ```powershell
   mvn spring-boot:run
   ```

## 7. Postman testing guide

**Base URL**: `http://localhost:8080/api/v1`

### 7.1 Passenger endpoints
- **POST** `/passengers`: `{"fullName":"John Doe", "email":"john@example.com", "phoneNumber":"1234567890"}`
- **GET** `/passengers/{id}`
- **GET** `/passengers?fullName=John&active=true`
- **PATCH** `/passengers/{id}/deactivate`

### 7.2 Journey endpoints
- **POST** `/journeys`:
```json
{
  "passengerId": 1,
  "journeyDate": "2026-11-15",
  "legs": [
    { "trainId": 1, "sourceStationId": 1, "destinationStationId": 3, "legSequence": 1 },
    { "trainId": 2, "sourceStationId": 3, "destinationStationId": 5, "legSequence": 2 }
  ]
}
```
- **GET** `/journeys/{id}`
- **GET** `/journeys/reference/{reference}`
- **GET** `/journeys/passenger/{passengerId}`
- **PATCH** `/journeys/{id}/cancel`

## 8. Complete sample workflow

1. **Get IDs**: Call `GET /api/v1/stations` and `GET /api/v1/trains` (Phase 1) to get valid IDs.
2. **Create Passenger**: Call `POST /api/v1/passengers`. Note the `id` (e.g., `1`).
3. **Plan Journey**: Call `POST /api/v1/journeys` with the passenger ID and valid train/station segments.
4. **Verify**: Call `GET /api/v1/journeys/reference/{ref}` to see the full itinerary.
5. **Cancel**: Call `PATCH /api/v1/journeys/{id}/cancel`.

## 9. Invalid test cases

| Scenario | Expected Status | Expected Message |
| :--- | :--- | :--- |
| Duplicate email | 409 Conflict | "Passenger with email '...' already exists" |
| Past journey date | 400 Bad Request | "Journey date cannot be in the past" |
| Identical stations in leg | 400 Bad Request | "Source and destination stations must be different" |
| Wrong station order | 400 Bad Request | "Source station must appear before destination..." |
| Disconnected legs | 400 Bad Request | "Leg N source must match previous leg destination" |
| Duplicate leg sequence | 400 Bad Request | "Duplicate leg sequence: N" |

## 10. Automated test results

Ran `mvn test` on 2026-10-25.

**Summary**:
- **Total Tests**: 50
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Build Status**: **SUCCESS**

**Key Test Classes**:
- `PassengerApiIntegrationTest`: 6 tests passed.
- `JourneyApiIntegrationTest`: 5 tests passed.

## 11. Troubleshooting

- **Migration Failures**: Check `flyway_schema_history` for failed checksums. Never edit `V4` once applied.
- **Duplicate Email**: Normalise to lowercase in the database if checking manually.
- **Foreign Key Failures**: Ensure the `passengerId`, `trainId`, and `stationId` actually exist before creating a journey.
- **Disconnected Legs**: Verify that `leg[N].destinationStationId` exactly matches `leg[N+1].sourceStationId`.

## 12. Git review and push

```powershell
git status
git diff
git add .
git commit -m "Complete TrainConcierge Phase 2 passenger journeys"
git push
```

**Recommended Commit Message**: `Complete TrainConcierge Phase 2 passenger journeys`
