# TrainConcierge — Phase 0: Project Foundation (Spring Boot + MySQL)

> Phase 0 only. No business modules (trains, passengers, journeys, disruptions, recommendations,
> hotels, notifications, authentication) are implemented in this phase.

---

## 1. Phase objective and implemented features

**Objective:** establish a clean, production-shaped Spring Boot 3 backend skeleton that starts up,
connects to MySQL, is observable, documents itself with OpenAPI, returns consistent error payloads,
and is covered by automated tests — with Flyway (not Hibernate) owning the database schema.

**Implemented / verified features**

| Feature | Status | Where |
|---|---|---|
| Application startup | Done — verified running on port 8080 | [TrainConciergeApplication.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/TrainConciergeApplication.java) |
| MySQL connectivity (HikariCP pool) | Done — verified live against MySQL 8.0.46 | [application.yml](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/application.yml) |
| Health endpoint (`/actuator/health`) | Done — returns `UP` with a `db` component | Actuator |
| Global exception handling | Done — `@RestControllerAdvice` | [GlobalExceptionHandler.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/exception/GlobalExceptionHandler.java) |
| Consistent error-response structure | Done — one JSON shape for 400/404/405/500 | [ApiErrorResponse.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/exception/ApiErrorResponse.java) |
| Basic request validation | Done — Jakarta Bean Validation on `@RequestBody` | [EchoRequest.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/dto/EchoRequest.java) |
| Swagger / OpenAPI configuration | Done — `/v3/api-docs` + `/swagger-ui.html` | [OpenApiConfig.java](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/java/com/trainconcierge/config/OpenApiConfig.java) |
| Environment-based configuration | Done — all DB settings from env vars with local defaults | `application.yml` |
| Flyway migration baseline | Done — `V1` applied (`flyway_schema_history` present, `success = 1`) | [V1__baseline.sql](file:///c:/Users/HP/Documents/trae_projects/Railway_New/src/main/resources/db/migration/V1__baseline.sql) |
| `.gitignore` (build output, IDE, logs, secrets) | Done | [.gitignore](file:///c:/Users/HP/Documents/trae_projects/Railway_New/.gitignore) |
| Basic application tests | Done — 11 tests, all passing | `src/test/java` |

**Two Phase 0 endpoints** were added purely as foundation proof (they are not business features):

- `GET /api/v1/system/status` — proves the app is alive **and** that the MySQL connection is real.
- `POST /api/v1/system/echo` — proves Bean Validation + the global error envelope work end to end.

---

## 2. Created / modified files

No pre-existing files were modified or deleted — the workspace was empty.

| File | Purpose |
|---|---|
| `pom.xml` | Maven build. Spring Boot 3.5.14 parent, Java 17, MySQL driver, JPA, Validation, Actuator, Flyway (`flyway-core` + `flyway-mysql`), springdoc, test starter. |
| `.gitignore` | Ignores `target/`, IDE files, logs, local secrets (`application-local.*`, `.env`, `*.pem`). |
| `src/main/resources/application.yml` | Single config source. MySQL URL/credentials from `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`; Hikari pool; `ddl-auto: validate`; Flyway settings; Actuator exposure (`health,info`); springdoc settings. |
| `src/main/resources/db/migration/V1__baseline.sql` | Flyway baseline. Creates **no** business tables; makes Flyway own the schema from run #1. |
| `src/main/java/com/trainconcierge/TrainConciergeApplication.java` | `@SpringBootApplication` entry point. |
| `src/main/java/com/trainconcierge/config/OpenApiConfig.java` | OpenAPI metadata bean (title, version, description). |
| `src/main/java/com/trainconcierge/controller/SystemController.java` | Foundation endpoints: `/api/v1/system/status`, `/api/v1/system/echo`. |
| `src/main/java/com/trainconcierge/service/SystemStatusService.java` | Obtains a pooled JDBC connection, validates it, and reads the database product/version. Keeps the controller thin. |
| `src/main/java/com/trainconcierge/dto/SystemStatusResponse.java` | Response DTO for the status endpoint. |
| `src/main/java/com/trainconcierge/dto/EchoRequest.java` | Validated request DTO (`@NotBlank`, `@Size(max = 200)`). |
| `src/main/java/com/trainconcierge/dto/EchoResponse.java` | Response DTO for the echo endpoint. |
| `src/main/java/com/trainconcierge/exception/ApiErrorResponse.java` | The single error envelope used by every failure path. |
| `src/main/java/com/trainconcierge/exception/GlobalExceptionHandler.java` | Maps validation, malformed body, type mismatch, not-found, method-not-allowed and unexpected errors to the envelope. |
| `src/main/java/com/trainconcierge/exception/ResourceNotFoundException.java` | 404 signal reserved for later phases' business code. |
| `src/test/java/com/trainconcierge/TrainConciergeApplicationTests.java` | `@SpringBootTest` context test (**requires MySQL**). |
| `src/test/java/com/trainconcierge/controller/SystemControllerWebMvcTest.java` | `@WebMvcTest` slice — status, echo, 400, 404, 405 (**no MySQL needed**). |
| `src/test/java/com/trainconcierge/dto/EchoRequestValidationTest.java` | Pure unit tests for the validation rules (**no MySQL needed**). |
| `phase0.md` | This document. |

**Deliberately NOT created:** no `entity/` and no `repository/` packages. Phase 0 defines no business
tables/aggregates, so creating them would be empty scaffolding. They arrive with the first real
business feature in a later phase.

---

## 3. Required versions

| Tool | Version used | Notes |
|---|---|---|
| JDK | **Java 17** (Temurin 17.0.20) | see note below |
| Maven | 3.9.16 | |
| MySQL Server | 8.0.46 (`MySQL80` service, port 3306) | MySQL 8.x required |
| Spring Boot | 3.5.14 | latest 3.x line at the time of writing |
| Hibernate ORM | 6.6.49.Final | via Spring Boot BOM |
| Flyway | 11.7.2 (`flyway-core` + `flyway-mysql`) | via Spring Boot BOM |
| MySQL Connector/J | 9.7.0 | via Spring Boot BOM; verified against MySQL 8.0.46 |
| springdoc-openapi | 2.8.17 | the line compatible with Spring Boot 3.5.x |
| Embedded Tomcat | 10.1.54 | |

### Why Java 17 and not Java 21

Java 21 was requested "if supported by the existing project; otherwise preserve its current compatible
version". Findings:

- The workspace contained **no existing project** (empty directory), so there was no pre-existing version to preserve.
- **No JDK 21 is installed on this machine.** `java -version` and `mvn -v` both report
  `Temurin 17.0.20`; Maven itself runs on JDK 17.
- Spring Boot 3.5.x **does** support Java 21 (and 17). It is therefore a *choice*, not a requirement.

**Decision: stay on Java 17.** Compiling with `release 17` is fully supported by Spring Boot 3.5.x,
builds and runs here without installing anything new, and produces bytecode that also runs on Java 21
later. Upgrading to 21 now would add an install step and risk with zero functional gain for Phase 0.
When you want Java 21, install a JDK 21, set `<java.version>21</java.version>` in `pom.xml`, and point
`JAVA_HOME` at it — no source changes are needed.

---

## 4. MySQL database creation

Start a MySQL client (MySQL Workbench, or the bundled CLI) and run:

```sql
CREATE DATABASE trainconcierge_db;
```

Recommended (matches what was actually used, sensible UTF-8 default):

```sql
CREATE DATABASE trainconcierge_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Verify:

```sql
SHOW DATABASES LIKE 'trainconcierge_db';
```

From a terminal (Windows PowerShell):

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "CREATE DATABASE IF NOT EXISTS trainconcierge_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

**Do not create any tables by hand.** Flyway creates `flyway_schema_history` on first startup and
applies `V1__baseline.sql`. After the first run, `SHOW TABLES;` in `trainconcierge_db` returns exactly:

```
flyway_schema_history
```

### Optional: dedicated app user instead of `root`

```sql
CREATE USER 'trainconcierge'@'localhost' IDENTIFIED BY 'change-me-strong-password';
GRANT ALL PRIVILEGES ON trainconcierge_db.* TO 'trainconcierge'@'localhost';
FLUSH PRIVILEGES;
```

Then set `DB_USERNAME=trainconcierge` and `DB_PASSWORD=change-me-strong-password`.

> Flyway needs DDL rights (CREATE/ALTER/DROP) on `trainconcierge_db`, so `ALL PRIVILEGES` on that one
> schema is the simplest correct grant.

---

## 5. Environment variables

The application reads exactly these names, with safe local defaults for host, port, database name and
username. The password has **no default** and is never written to any file in this repository.

| Variable | Default | Notes |
|---|---|---|
| `DB_HOST` | `localhost` | |
| `DB_PORT` | `3306` | |
| `DB_NAME` | `trainconcierge_db` | |
| `DB_USERNAME` | `root` | override in shared environments |
| `DB_PASSWORD` | *(empty)* | **must** be supplied; never commit it |
| `SERVER_PORT` | `8080` | optional |

### Windows PowerShell

Per-session (recommended — nothing is persisted):

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="trainconcierge_db"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-mysql-password"
```

### Linux / macOS

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=trainconcierge_db
export DB_USERNAME=root
export DB_PASSWORD='your-mysql-password'
```

### IntelliJ IDEA run configuration

Run → Edit Configurations → Environment variables:

```
DB_HOST=localhost;DB_PORT=3306;DB_NAME=trainconcierge_db;DB_USERNAME=root;DB_PASSWORD=your-mysql-password
```

---

## 6. How to start the application

Prerequisite: the `trainconcierge_db` database exists (section 4) and the variables in section 5 are set
in the same terminal.

**Option A — Maven (development):**

```powershell
mvn spring-boot:run
```

**Option B — packaged jar (closest to production):**

```powershell
mvn clean package
java -jar target\trainconcierge-0.0.1-SNAPSHOT.jar
```

**Expected startup log (abridged, real output):**

```
HikariDataSource : trainconcierge-hikari - Starting...
FlywayExecutor   : Database: jdbc:mysql://localhost:3306/trainconcierge_db?... (MySQL 8.0)
DbValidate       : Successfully validated 1 migration
DbMigrate        : Migrating schema `trainconcierge_db` to version "1 - baseline"
DbMigrate        : Successfully applied 1 migration to schema `trainconcierge_db`, now at version v1
TomcatWebServer  : Tomcat started on port 8080 (http) with context path '/'
TrainConciergeApplication : Started TrainConciergeApplication in 3.627 seconds
```

On a second start you will instead see `Current version of schema 'trainconcierge_db': 1` and
`Schema 'trainconcierge_db' is up to date. No migration necessary.` — proof that migrations are idempotent.

Stop it with `Ctrl + C`.

---

## 7. Postman requests

Base URL: `http://localhost:8080`

### 7.1 `GET /actuator/health` — app + database health

- **Method / URL:** `GET http://localhost:8080/actuator/health`
- **Headers:** none
- **Body:** none
- **Expected status:** `200 OK`

```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"],
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "total": 249067204608, "free": 1486819328, "threshold": 10485760, "path": "C:\\Users\\HP\\Documents\\trae_projects\\Railway_New\\.", "exists": true } },
    "livenessState": { "status": "UP" },
    "ping": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "ssl": { "status": "UP", "details": { "validChains": [], "invalidChains": [] } }
  }
}
```

> `components.db.status = "UP"` is the direct proof that MySQL connectivity works. If MySQL is down the
> overall status becomes `DOWN` and `db.details.error` explains why.

### 7.2 `GET /actuator/info` — application build metadata

- **Method / URL:** `GET http://localhost:8080/actuator/info`
- **Expected status:** `200 OK`

```json
{"app":{"name":"TrainConcierge","version":"0.0.1-SNAPSHOT","description":"TrainConcierge backend foundation (Phase 0)"}}
```

### 7.3 `GET /api/v1/system/status` — application + DB status

- **Method / URL:** `GET http://localhost:8080/api/v1/system/status`
- **Headers:** none
- **Expected status:** `200 OK`

```json
{
  "application": "trainconcierge",
  "version": "0.0.1-SNAPSHOT",
  "checkedAt": "2026-10-25T22:18:15.4665007+05:30",
  "databaseConnected": true,
  "databaseProduct": "MySQL",
  "databaseVersion": "8.0.46"
}
```

### 7.4 `POST /api/v1/system/echo` — happy path

- **Method / URL:** `POST http://localhost:8080/api/v1/system/echo`
- **Headers:** `Content-Type: application/json`
- **Body → raw → JSON:**

```json
{ "message": "Delhi to Mumbai" }
```

- **Expected status:** `200 OK`

```json
{
  "message": "Delhi to Mumbai",
  "messageLength": 15,
  "processedAt": "2026-10-25T22:18:15.5348142+05:30"
}
```

### 7.5 `POST /api/v1/system/echo` — validation failure (blank message)

- **Same URL/headers**, body:

```json
{ "message": "   " }
```

- **Expected status:** `400 Bad Request`

```json
{
  "timestamp": "2026-10-25T22:18:15.5467694+05:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/system/echo",
  "fieldErrors": { "message": "message must not be blank" }
}
```

### 7.6 `POST /api/v1/system/echo` — malformed JSON

- **Same URL/headers**, body: `{"message":`
- **Expected status:** `400 Bad Request`

```json
{
  "timestamp": "2026-10-25T22:18:15.5724257+05:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Request body is missing or malformed",
  "path": "/api/v1/system/echo"
}
```

> Note: `fieldErrors` is omitted (not `null`) when it does not apply.

### 7.7 `GET /api/v1/system/echo` — wrong HTTP method

- **Method / URL:** `GET http://localhost:8080/api/v1/system/echo`
- **Expected status:** `405 Method Not Allowed`

```json
{
  "timestamp": "2026-10-25T22:18:15.5795714+05:30",
  "status": 405,
  "error": "Method Not Allowed",
  "message": "HTTP method GET is not supported for this endpoint. Supported: POST",
  "path": "/api/v1/system/echo"
}
```

### 7.8 `GET /api/v1/unknown` — unknown path

- **Method / URL:** `GET http://localhost:8080/api/v1/unknown`
- **Expected status:** `404 Not Found`

```json
{
  "timestamp": "2026-10-25T22:18:15.5899252+05:30",
  "status": 404,
  "error": "Not Found",
  "message": "No endpoint found for this request",
  "path": "/api/v1/unknown"
}
```

### 7.9 `GET /v3/api-docs` — OpenAPI document

- **Method / URL:** `GET http://localhost:8080/v3/api-docs`
- **Expected status:** `200 OK`; the `paths` object contains exactly:

```
/api/v1/system/echo
/api/v1/system/status
```

### 7.10 `GET /swagger-ui.html`

- **Method / URL:** `GET http://localhost:8080/swagger-ui.html`
- **Expected status:** `302 Found`, `Location: http://localhost:8080/swagger-ui/index.html`
  (Postman follows redirects by default and shows the Swagger UI HTML.)

---

## 8. Testing the health endpoint and Swagger UI

**Health endpoint**

1. Browser: open <http://localhost:8080/actuator/health> → `{"status":"UP", ...}`.
2. Postman: `GET http://localhost:8080/actuator/health` → `200`, `components.db.status = "UP"`.
3. Liveness / readiness probes (enabled in Phase 0):
   - `GET http://localhost:8080/actuator/health/liveness`
   - `GET http://localhost:8080/actuator/health/readiness`
4. Negative check: stop the MySQL service (`Stop-Service MySQL80` as Administrator), call the endpoint
   again — `components.db.status` flips to `DOWN`, which proves the health check is really hitting MySQL
   and is not a hard-coded `UP`.
5. Only `health` and `info` are exposed (`management.endpoints.web.exposure.include: health,info`).
   `GET /actuator/env` or `/actuator/beans` correctly return `404`.

**Swagger UI**

1. Browser: open <http://localhost:8080/swagger-ui.html> → redirected to
   <http://localhost:8080/swagger-ui/index.html>.
2. You should see the **System** tag with both endpoints; expand `POST /api/v1/system/echo` → *Try it out*
   → body `{"message":"hello"}` → *Execute* → `200`.
3. Raw document: <http://localhost:8080/v3/api-docs>.

---

## 9. Tests actually run and their real results

Every command below was executed in this workspace on Windows with MySQL 8.0.46 running and
`trainconcierge_db` created. Results are pasted from the real output — nothing is claimed that was not run.

### 9.1 Build + tests against live MySQL

```powershell
mvn -B -ntp clean test
```

```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in SystemControllerWebMvcTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in EchoRequestValidationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in TrainConciergeApplicationTests
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 19.268 s
```

### 9.2 Full package build (same tests + fat jar)

```powershell
mvn -B -ntp clean package
```

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: C:\Users\HP\Documents\trae_projects\Railway_New\target\trainconcierge-0.0.1-SNAPSHOT.jar
[INFO] BUILD SUCCESS
```

Produced artifact: `target\trainconcierge-0.0.1-SNAPSHOT.jar` (61,930,404 bytes).

### 9.3 MySQL-dependent vs. MySQL-independent tests

This distinction matters: **MySQL was never replaced by H2 or PostgreSQL**, and no test silently
falls back to another engine.

| Test class | Needs MySQL? | Why | Result |
|---|---|---|---|
| `TrainConciergeApplicationTests` (`@SpringBootTest`) | **Yes** | Boots the full context: Hikari pool, Flyway migration, JPA `EntityManagerFactory`. | 1 passed |
| `SystemControllerWebMvcTest` (`@WebMvcTest`) | No | Web slice only; `SystemStatusService` is mocked (`@MockitoBean`), JPA/Flyway are not started. | 6 passed |
| `EchoRequestValidationTest` (plain JUnit 5) | No | Uses `Validation.buildDefaultValidatorFactory()` directly. | 4 passed |

If MySQL is unavailable, `TrainConciergeApplicationTests` **fails** (it cannot get a connection) while the
other 10 tests still pass. To run only the MySQL-independent tests:

```powershell
mvn -B -ntp test "-Dtest=SystemControllerWebMvcTest,EchoRequestValidationTest" -DfailIfNoTests=false
```

### 9.4 Live runtime verification (not just tests)

The packaged application was started and every endpoint in section 7 was called over HTTP:

| Call | Actual result |
|---|---|
| `GET /actuator/health` | `200`, `status: UP`, `db: UP (MySQL)` |
| `GET /actuator/info` | `200` |
| `GET /api/v1/system/status` | `200`, `databaseConnected: true`, `databaseVersion: 8.0.46` |
| `POST /api/v1/system/echo` valid | `200`, `messageLength: 15` |
| `POST /api/v1/system/echo` blank | `400` + `fieldErrors.message` |
| `POST /api/v1/system/echo` malformed | `400` |
| `GET /api/v1/system/echo` | `405` |
| `GET /api/v1/unknown` | `404` |
| `GET /v3/api-docs` | `200`, 2 paths |
| `GET /swagger-ui.html` | `302 → /swagger-ui/index.html` |

Database state after the runs:

```
mysql> SHOW TABLES;
flyway_schema_history

mysql> SELECT installed_rank, version, description, type, success FROM flyway_schema_history;
1 | 1 | baseline | SQL | 1
```

---

## 10. Troubleshooting

### 10.1 `Communications link failure` / `Connection refused` / startup hangs then fails

MySQL is not running or not on the expected port.

```powershell
Get-Service MySQL80
Start-Service MySQL80            # needs Administrator
Test-NetConnection -ComputerName localhost -Port 3306
```

Confirm `DB_HOST`/`DB_PORT` match the server. A wrong port (e.g. `3307`) produces exactly this error.

### 10.2 `Access denied for user 'root'@'localhost' (using password: NO/YES)` — MySQL 1045

Wrong or missing password.

- The password is read only from `DB_PASSWORD`. If it is unset, the app connects with an empty password,
  which almost always fails.
- PowerShell: set it in the **same** terminal that runs `mvn` (`$env:DB_PASSWORD="..."`). Environment
  variables set in one terminal window do not affect another.
- Verify independently:
  `& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "SELECT 1;"`
- Special characters: wrap the value in single quotes in PowerShell (`$env:DB_PASSWORD='P@t!3nc3'`) so
  `$`, `@`, `!` are not interpreted.

### 10.3 `Unknown database 'trainconcierge_db'` — MySQL 1049

The database does not exist; Spring Boot does not create it. Run section 4:

```sql
CREATE DATABASE trainconcierge_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

If you intentionally used a different name, set `DB_NAME` accordingly.

### 10.4 Port already in use — `Web server failed to start. Port 8080 was already in use`

Find and free the port, or move the app:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object OwningProcess
Stop-Process -Id <OwningProcess>          # only if you own that process
$env:SERVER_PORT="8081"
mvn spring-boot:run
```

A previously started `mvn spring-boot:run` that was not stopped is the most common cause.

### 10.5 Flyway errors

| Message | Cause | Fix |
|---|---|---|
| `Found non-empty schema "trainconcierge_db" but no schema history table` | Tables exist without Flyway having created them (e.g. manual tables or an earlier `ddl-auto: create`). | Either drop the stray tables, or temporarily set `spring.flyway.baseline-on-migrate: true` to adopt the schema. |
| `Validate failed: Migration checksum mismatch for migration version 1` | `V1__baseline.sql` was edited after being applied. | Never edit an applied migration. Restore the file, or for a local-only DB run `mvn flyway:repair` / delete `flyway_schema_history` and drop the schema, then restart. |
| `Detected failed migration to version 1` | A migration failed mid-run. | Inspect the error, fix the SQL, then `mvn flyway:repair` (or drop the database and recreate) and restart. |
| `Unable to obtain connection from database` / Flyway starts before DB is reachable | MySQL down/incorrect credentials. | Fix 10.1/10.2 — Flyway fails fast **by design**, so a bad DB connection stops startup. |
| `Schema "trainconcierge_db" has a version (1) that is newer than the latest available migration` | Running an older build against a newer schema. | Check out the matching code revision, or add the missing migration. |

Note: `spring.jpa.hibernate.ddl-auto` is `validate` and must stay that way — Flyway is the single owner
of schema changes. Never set it to `create`, `create-drop` or `update`.

### 10.6 `HHH000204`/dialect or connection-pool warnings

Informational only. The dialect is auto-detected (`hibernate.dialect` is deliberately not set
explicitly). Hikari pool settings live under `spring.datasource.hikari` and can be tuned there.

---

## 11. Git commands

**Current state of this repository (verified):**

| Item | Value |
|---|---|
| Repository root | `C:\Users\HP\Documents\trae_projects\Railway_New` (the project has its own `.git`) |
| Branch | `main`, tracking `origin/main` |
| Remote `origin` | `https://github.com/asiyamujawar/Train-.git` |
| First commit | `35c3d0d Complete TrainConcierge Phase 0 foundation` — 18 files, pushed |
| `target/` | ignored — verified with `git check-ignore -v target/` |

> ### ⚠️ One thing to be aware of
>
> A stray, **empty** Git repository also exists at the drive root `C:\` (`C:\.git`). It has **no commits**
> and its `origin` points at a *different* repository (`github-profile.git`). It is unrelated to this
> project and is not used by the commands below, but:
> - running `git status` **from `C:\`** still lists your whole drive;
> - **never** run `git add -A`, `git add .` or `git commit -a` from `C:\`.
>
> You can safely leave it alone. If you want it gone (it has zero commits, so nothing is lost):
> `Remove-Item -Recurse -Force C:\.git` — only if you are certain nothing else in it matters.

### Review before staging (run from the project folder)

```powershell
git status
git diff
```

A freshly committed tree shows `nothing to commit, working tree clean`.

No secrets are tracked — `application.yml` contains only `${DB_PASSWORD:}` placeholders and **no real
password is committed**. Never commit `.env`, `application-local.yml`, keystores, `*.pem` files, or real
credentials.

### Stage the reviewed files explicitly (no `-A`, no `.`)

```powershell
git add .gitignore pom.xml phase0.md
git add src/main/java/com/trainconcierge
git add src/main/resources
git add src/test/java/com/trainconcierge
git status
```

### Commit

```powershell
git commit -m "Complete TrainConcierge Phase 0 foundation"
```

### Push

```powershell
git push
```

The upstream is already configured, so `-u` is not needed again. For a branch that has no upstream:

```powershell
git push -u origin HEAD
```

**Nothing is committed or pushed automatically in later phases** — run the commands above yourself.

---

## 12. Recommended commit message

```
Complete TrainConcierge Phase 0 foundation
```
