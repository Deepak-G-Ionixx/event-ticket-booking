# Event Ticket Booking Service

A Spring Boot REST API for publishing events, booking numbered seats, applying pricing rules automatically, and reporting how well each event is selling. It is secured with JWT and runs on PostgreSQL, with Docker support and a GitHub Actions CI pipeline.

## Table of Contents

1. [What the application can do](#1-what-the-application-can-do)
2. [Tech stack](#2-tech-stack)
3. [Prerequisites](#3-prerequisites)
4. [Configuration (`local.properties`)](#4-configuration-localproperties)
5. [Run with Docker (app + database)](#5-run-with-docker-app--database)
6. [Run locally (database in Docker, app from Gradle/IntelliJ)](#6-run-locally-database-in-docker-app-from-gradleintellij)
7. [Connect to the database with DBeaver](#7-connect-to-the-database-with-dbeaver)
8. [Database migrations with Liquibase](#8-database-migrations-with-liquibase)
9. [API endpoints](#9-api-endpoints)
10. [Business rules](#10-business-rules)
11. [Authentication and roles](#11-authentication-and-roles)
12. [Error responses](#12-error-responses)
13. [Swagger UI and health check](#13-swagger-ui-and-health-check)
14. [Logging](#14-logging)
15. [Testing and coverage](#15-testing-and-coverage)
16. [Code formatting with Spotless](#16-code-formatting-with-spotless)
17. [Continuous integration (GitHub Actions)](#17-continuous-integration-github-actions)
18. [Architecture decision record (ADR)](#18-architecture-decision-record-adr)
19. [Project structure](#19-project-structure)
20. [Self-test checklist](#20-self-test-checklist)
21. [Useful commands](#21-useful-commands)

\---

## 1\. What the application can do

**Organisers** can:

* Create, update and delete their own events (an event with bookings cannot be deleted).
* List their own events (paginated).
* View all bookings for their events, filtered by status and sorted newest first (paginated).
* View a sales summary: seats sold, revenue, occupancy % and demand band.

**Customers** can:

* Browse any event and view its seat map (booked and available seats).
* Book 1 to 6 numbered seats. Pricing rules, discounts and seat limits are applied automatically.
* View a single booking and list their own bookings (paginated).

**Everyone** can:

* Register an account, log in to receive a JWT, and reset a forgotten password by email.

**Built-in protections:**

* Two customers can never confirm the same seat. This is enforced by a unique database constraint and proven by a concurrency test.
* Rejected bookings are saved for the record but hold no seats.
* No stack trace is ever returned to the client.

## 2\. Tech stack

|Area|Technology|
|-|-|
|Language|Java 21|
|Framework|Spring Boot (Web MVC, Data JPA, Validation, Security, Actuator, Mail)|
|ORM|Hibernate (`ddl-auto=validate`)|
|Database|PostgreSQL 16|
|Migrations|Liquibase (YAML changelogs)|
|Authentication|JWT (jjwt) and BCrypt password hashing|
|API docs|springdoc OpenAPI / Swagger UI|
|Logging|SLF4J, Logback, Logstash JSON encoder, MDC request id|
|Build|Gradle (Kotlin DSL)|
|Formatting|Spotless|
|Testing|JUnit 5, Mockito, Spring Boot Test, Testcontainers, JaCoCo|
|Containers|Docker, Docker Compose|
|CI|GitHub Actions|

## 3\. Prerequisites

* JDK 21 (Temurin recommended). Check with `java -version`.
* Docker Desktop, running. Check with `docker --version` and `docker compose version`.
* Git.
* IntelliJ IDEA (or VS Code with the Java extension pack).
* Postman or Bruno for API testing.
* DBeaver (optional) to browse the database.

Gradle does not need to be installed; the project includes the Gradle wrapper (`gradlew` / `gradlew.bat`).

Clone the repository:

```powershell
git clone https://github.com/Deepak-G-Ionixx/event-ticket-booking.git
cd event-ticket-booking
```

## 4\. Configuration (`local.properties`)

All secrets come from environment variables. Nothing secret is committed to the repository.

1. Copy the example file:

```powershell
   copy local\\\_properties.example local.properties
   ```

On Linux or macOS the example file is named `local.properties.example`.

2. Edit `local.properties` and set real values:

|Key|Meaning|
|-|-|
|`POSTGRES\\\_DB`|Database name (`eventticketdb`)|
|`POSTGRES\\\_USER`|Database user (`postgres`)|
|`POSTGRES\\\_PASSWORD`|Database password. Choose your own.|
|`JWT\\\_SECRET`|Signing secret, at least 32 characters. Generate a random one.|
|`JWT\\\_EXPIRATION`|Token lifetime (default `60m`)|
|`JWT\\\_ISSUER`|Token issuer (default `event-ticket-booking`)|
|`SEED\\\_DEMO\\\_USERS`|`true` creates two demo accounts. Keep `false` outside your own machine.|
|`MAIL\\\_\\\*`|Optional. Only needed for forgot-password emails.|

Generate a JWT secret in PowerShell:

```powershell
   $b = New-Object byte\\\[] 48; \\\[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); \\\[Convert]::ToBase64String($b)
   ```

3. Never commit `local.properties`. It is already listed in `.gitignore` and `.dockerignore`.

## 5\. Run with Docker (app + database)

This starts both PostgreSQL and the application. Liquibase creates the schema automatically on startup.

```powershell
docker compose up -d --build
```

|Service|Container port|Host port|URL|
|-|-|-|-|
|PostgreSQL 16|5432|**5433**|`localhost:5433`|
|Application|8080|**8080**|http://localhost:8080|

Useful checks:

```powershell
docker compose ps                  # both services should be "healthy"
docker compose logs -f app         # follow application logs
docker compose logs postgres       # database logs
```

Details of the setup:

* The `app` service waits for the PostgreSQL health check (`pg\\\_isready`) before starting.
* The app runs with `SPRING\\\_PROFILES\\\_ACTIVE=docker`. Inside Docker it reaches the database at host `postgres`, port `5432`.
* The database is stored in the named volume `postgres\\\_data`. Application logs are stored in `app\\\_logs`.
* The container runs as a non-root user, and the timezone is set to `Asia/Kolkata`.

Stop everything:

```powershell
docker compose down        # stop, keep the database data
docker compose down -v     # stop AND delete the database volume (full reset)
```

> \\\*\\\*Warning:\\\*\\\* `-v` permanently deletes all database data.

## 6\. Run locally (database in Docker, app from Gradle/IntelliJ)

Use this while developing.

**Step 1: start only the database**

```powershell
docker compose up -d postgres
```

**Step 2: set the environment variables** (PowerShell, same window you run Gradle in):

```powershell
$env:DB\\\_PASSWORD = "<your POSTGRES\\\_PASSWORD>"
$env:JWT\\\_SECRET  = "<your JWT secret, 32+ characters>"
$env:SEED\\\_DEMO\\\_USERS = "true"    # optional, creates demo accounts
```

The default JDBC URL is `jdbc:postgresql://localhost:5433/eventticketdb` and the default user is `postgres`. Override them with `DB\\\_URL` and `DB\\\_USERNAME` if needed.

In IntelliJ, put the same variables under **Run > Edit Configurations > Environment variables**.

**Step 3: run**

```powershell
.\\\\gradlew.bat bootRun
```

The app starts at http://localhost:8080.

> \\\*\\\*Do not run the Docker `app` container and `bootRun` at the same time.\\\*\\\* Both use port 8080.

### Demo accounts (only when `SEED\\\_DEMO\\\_USERS=true`)

|Role|Email|Password|
|-|-|-|
|ORGANISER|`organiser@example.com`|`Organiser@123`|
|CUSTOMER|`customer@example.com`|`Customer@123`|

These passwords are publicly known. Use them only on your own machine.

## 7\. Connect to the database with DBeaver

The database is exposed on host port **5433**, not the usual 5432.

1. Make sure the container is running: `docker compose ps`.
2. In DBeaver choose **Database > New Database Connection > PostgreSQL**.
3. Enter:

|Field|Value|
|-|-|
|Host|`localhost`|
|Port|`5433`|
|Database|`eventticketdb`|
|Username|`postgres`|
|Password|the `POSTGRES\\\_PASSWORD` from your `local.properties`|

4. Click **Test Connection**. DBeaver may offer to download the PostgreSQL driver the first time; accept.
5. Click **Finish**. Tables are under **eventticketdb > Schemas > public > Tables**.

Tables created by Liquibase:

|Table|Purpose|
|-|-|
|`event`|Events (name, venue, category, date, seats, base price, owner)|
|`booking`|Bookings, both CONFIRMED and REJECTED|
|`booking\\\_seat`|One row per seat held by a confirmed booking|
|`users`|Registered users and their role|
|`password\\\_reset\\\_tokens`|Hashed, expiring password-reset tokens|
|`databasechangelog`, `databasechangeloglock`|Liquibase bookkeeping|

If the connection fails, check that Docker is running, that the port is `5433`, and that the password matches `local.properties`.

## 8\. Database migrations with Liquibase

The schema is created **only** by Liquibase. Hibernate runs with `spring.jpa.hibernate.ddl-auto=validate`, so it checks that the entities match the schema but never creates or changes tables.

* Master changelog: `src/main/resources/db/changelog/db.changelog-master.yaml`
* Individual changesets: `src/main/resources/db/changelog/changes/`

|Changeset|Purpose|
|-|-|
|`002`|Create the `event` table with a unique `(name, event\\\_date)`|
|`003`, `004`|Add `event\\\_log\\\_id` (human-readable id such as `ETB0001`)|
|`005`|Create the `booking` table|
|`006`|Create the `users` table|
|`007`|Create `password\\\_reset\\\_tokens`|
|`008`|Add ownership columns (organiser and customer ids)|
|`009`|Create `booking\\\_seat` with unique `(event\\\_id, seat\\\_number)`|
|`010`, `011`|Enforce ownership and add booking indexes and consistency checks|

Rules:

* Migrations run automatically when the application starts.
* Never edit a changeset that has already run. Add a new numbered changeset instead.

## 9\. API endpoints

All endpoints use JSON. Send `Authorization: Bearer <token>` on every request except `/auth/\\\*`, Swagger, and the health check.

### Authentication

|Method|Endpoint|Purpose|
|-|-|-|
|POST|`/auth/register`|Create an account (role defaults to CUSTOMER)|
|POST|`/auth/login`|Log in and receive a JWT|
|POST|`/auth/forgot-password`|Request a password-reset email|
|POST|`/auth/reset-password`|Set a new password using the reset token|

### Events

|Method|Endpoint|Who|Purpose|
|-|-|-|-|
|POST|`/api/events`|ORGANISER|Create an event (201, 409 if duplicate)|
|GET|`/api/events/{id}`|Any logged-in user|Get an event|
|PUT|`/api/events/{id}`|ORGANISER (owner)|Update an event|
|DELETE|`/api/events/{id}`|ORGANISER (owner)|Delete an event with no bookings|
|GET|`/api/events/mine`|ORGANISER|List my events (paginated)|
|GET|`/api/events/{id}/bookings?status=`|ORGANISER (owner)|List bookings, newest first, paginated|
|GET|`/api/events/{id}/seats`|Any logged-in user|Booked and available seats|
|GET|`/api/events/{id}/summary`|ORGANISER (owner)|Sales summary|

### Bookings

|Method|Endpoint|Who|Purpose|
|-|-|-|-|
|POST|`/api/bookings`|CUSTOMER|Book seats (201 for both CONFIRMED and REJECTED)|
|GET|`/api/bookings/{id}`|Owner of the booking, or the event's organiser|Get a booking|
|GET|`/api/bookings/mine`|CUSTOMER|List my bookings (paginated)|

**Pagination parameters** (list endpoints): `page` (default 0), `size` (1 to 100, default 10), `sort` (for example `bookedOn,desc`).

### Example: create an event

`POST /api/events`

```json
{
  "name": "Chennai Jazz Night",
  "venue": "Music Academy, Chennai",
  "category": "MUSIC",
  "event\\\_date": "2026-12-20",
  "total\\\_seats": 200,
  "base\\\_price": 1500
}
```

Valid categories: `MUSIC`, `SPORTS`, `TECH`, `THEATRE`.

### Example: book seats

`POST /api/bookings`

```json
{
  "event\\\_id": 1,
  "customer\\\_name": "Priya Raman",
  "customer\\\_email": "priya@example.com",
  "seat\\\_numbers": \\\[12, 13, 14, 15]
}
```

Response (201):

```json
{
  "id": 7,
  "event\\\_id": 1,
  "customer\\\_name": "Priya Raman",
  "customer\\\_email": "priya@example.com",
  "seat\\\_numbers": \\\[12, 13, 14, 15],
  "seats": 4,
  "price\\\_per\\\_seat": 1282.50,
  "total\\\_amount": 5130.00,
  "status": "CONFIRMED",
  "rejection\\\_reason": null,
  "booked\\\_on": "2026-10-10"
}
```

### Example: seat map

`GET /api/events/1/seats`

```json
{
  "event\\\_id": 1,
  "total\\\_seats": 20,
  "booked\\\_seats": \\\[3, 4, 12, 13, 14, 15],
  "available\\\_seats": \\\[1, 2, 5, 6, 7, 8, 9, 10, 11, 16, 17, 18, 19, 20]
}
```

### Example: summary

`GET /api/events/1/summary`

```json
{
  "event\\\_id": 1,
  "event\\\_name": "Chennai Jazz Night",
  "total\\\_seats": 200,
  "seats\\\_sold": 164,
  "seats\\\_available": 36,
  "confirmed\\\_bookings": 52,
  "rejected\\\_bookings": 3,
  "total\\\_revenue": 231480.00,
  "occupancy\\\_pct": 82.0,
  "demand\\\_band": "HIGH"
}
```

## 10\. Business rules

**Event rules**

* `name` and `venue`: 3 to 100 characters.
* `category`: one of `MUSIC`, `SPORTS`, `TECH`, `THEATRE`.
* `event\\\_date`: must be in the future.
* `total\\\_seats`: 10 to 10,000. Seats are numbered 1 to `total\\\_seats`.
* `base\\\_price`: greater than 0.
* The same `name` and `event\\\_date` cannot exist twice (409 `DUPLICATE\\\_EVENT`).

**Booking validation (400 on failure)**

* `event\\\_id`: required, positive integer.
* `customer\\\_name`: 3 to 50 characters, letters and spaces only.
* `customer\\\_email`: valid email format.
* `seat\\\_numbers`: 1 to 6 positive integers, no duplicates, each within the event's `total\\\_seats` (otherwise `seat N does not exist for this event`).
* The event must exist (404).

**Rejection rules.** They run in this order and the first failure decides the reason:

|#|Rule|Rejection reason|
|-|-|-|
|1|The event date must be today or later|`Event is already over`|
|2|None of the requested seats may belong to a CONFIRMED booking|`Seats already booked: 12, 14` (all taken seats, ascending)|
|3|One customer may hold at most 6 confirmed seats per event|`Customer already holds the maximum seats for this event`|

Rejected bookings are saved with status `REJECTED` and hold no seats.

**Pricing**

|Discount|Condition|Effect|
|-|-|-|
|Early bird|Booked 30 or more days before the event|price × 0.90|
|Group|4 or more seats|price × 0.95 (applied after early bird)|

The price per seat is rounded to 2 decimals, and `total\\\_amount = price\\\_per\\\_seat × seats`, also rounded to 2 decimals.

Worked example: base price 1500, event 71 days away, 4 seats gives 1500 × 0.90 = 1350.00, then 1350.00 × 0.95 = **1282.50** per seat, **5130.00** total.

**Summary rules**

* `seats\\\_sold` and `total\\\_revenue` count CONFIRMED bookings only.
* `occupancy\\\_pct = seats\\\_sold ÷ total\\\_seats × 100`, rounded to 1 decimal.
* `demand\\\_band`: `LOW` below 40, `MEDIUM` from 40 to 75 inclusive, `HIGH` above 75.
* An event with no bookings returns zeros and `LOW`.

## 11\. Authentication and roles

* Stateless JWT authentication. There are no cookie sessions.
* Passwords are hashed with BCrypt (8 to 72 characters).
* Two roles: `ORGANISER` and `CUSTOMER`.
* `401 Unauthorized`: missing, invalid or expired token.
* `403 Forbidden`: logged in, but the wrong role or not the owner.
* An organiser can only see and change their own events. A customer can only see their own bookings.
* The booking owner comes from the JWT, never from the request body.

Log in:

```http
POST /auth/login
{ "email": "customer@example.com", "password": "Customer@123" }
```

Then send the returned token on later requests:

```http
Authorization: Bearer <token>
```

In Swagger UI, click **Authorize** and paste the token.

## 12\. Error responses

Validation failures list **every** bad field, not just the first:

```json
{
  "error": "VALIDATION\\\_FAILED",
  "details": \\\[
    { "field": "category", "message": "must be one of MUSIC, SPORTS, TECH, THEATRE" },
    { "field": "total\\\_seats", "message": "must be between 10 and 10000" }
  ]
}
```

|Status|`error`|When|
|-|-|-|
|400|`VALIDATION\\\_FAILED`|One or more fields are invalid|
|400|`INVALID\\\_REQUEST`|Malformed or empty JSON, bad id, bad `status` filter|
|401|`UNAUTHORIZED`|Missing or invalid token|
|403|`FORBIDDEN`|Wrong role or not the owner|
|404|`NOT\\\_FOUND`|Event or booking does not exist|
|409|`DUPLICATE\\\_EVENT`|Same name and date already exists|
|409|`SEAT\\\_ALREADY\\\_BOOKED`|Two requests raced for the same seat|
|500|`INTERNAL\\\_ERROR`|Unexpected failure. No stack trace is returned.|

Every response carries an `X-Request-Id` header, and the same id appears in the logs.

## 13\. Swagger UI and health check

|Page|URL|
|-|-|
|Swagger UI|http://localhost:8080/swagger-ui.html|
|OpenAPI JSON|http://localhost:8080/v3/api-docs|
|Health check|http://localhost:8080/actuator/health|

A healthy application returns `{"status":"UP"}`.

## 14\. Logging

* Logs are written as JSON, to the console and to `logs/event-ticket-booking.json`. The location can be changed with `LOG\\\_DIR`.
* Files roll at 10 MB, keep 30 days of history, and are capped at 1 GB in total.
* Every log line carries `requestId`, `method` and `path`. Events add `eventLogId` (for example `ETB0001`) and authenticated requests add `userId`.
* A client may send its own `X-Request-Id` header. Otherwise one is generated.

## 15\. Testing and coverage

```powershell
.\\\\gradlew.bat test           # run all tests
.\\\\gradlew.bat clean test     # clean build and run tests
.\\\\gradlew.bat check          # tests plus the coverage rule
```

* **Unit tests** (JUnit 5 and Mockito): every booking rule, both discounts and their combination, the per-customer limit, and demand bands.
* **Slice and controller tests**: every endpoint for 400, 401, 403, 404 and 409.
* **Integration tests** (`@SpringBootTest` and Testcontainers): a real PostgreSQL runs in a throwaway container for the book, seat map and summary flow, security, ownership, pagination and schema integrity.
* **Concurrency tests**: prove that two bookings cannot confirm the same seat, or exceed the per-customer limit.
* **Coverage**: JaCoCo fails the build if line coverage is below **90%**. The HTML report is at `build/reports/jacoco/test/html/index.html`.

Docker Desktop must be running for the integration tests. The test JVM is pinned to `-Duser.timezone=Asia/Kolkata`, because Windows reports the legacy name `Asia/Calcutta`, which the `postgres:16` image rejects. If `JWT\\\_SECRET` is not set, the build uses a throwaway random value, so tests need no secrets.

## 16\. Code formatting with Spotless

Spotless checks that Java files have no trailing whitespace and end with a newline. Line endings follow `.gitattributes` (LF).

```powershell
.\\\\gradlew.bat spotlessCheck    # report problems (CI runs this)
.\\\\gradlew.bat spotlessApply    # fix problems automatically
```

Run `spotlessApply` before every commit so CI does not fail.

## 17\. Continuous integration (GitHub Actions)

The workflow in `.github/workflows/` runs on every pull request:

1. Check out the code and set up JDK 21.
2. Run `./gradlew spotlessCheck`.
3. Run `./gradlew test` (Testcontainers works on GitHub's runners).
4. Enforce the JaCoCo coverage threshold.

No secrets are stored in the repository. CI uses a generated `JWT\\\_SECRET`.

## 18\. Architecture decision record (ADR)

### ADR-001: JWT authentication instead of server-side sessions

**Status:** Accepted

**Context**
The service is a REST API used by browsers, Postman and possibly mobile apps. It needs two roles (ORGANISER and CUSTOMER), and it should be easy to run as several containers.

**Options considered**

1. *Server-side sessions (cookies).* Simple, and logout is immediate. But the session lives in server memory, so several instances would need a shared session store (Redis or the database). Cookies also bring CSRF concerns.
2. *JWT bearer tokens.* The server stores nothing. Any instance can verify a token with the shared secret. Clients send the token in the `Authorization` header, so CSRF does not apply.

**Decision**
Use stateless JWTs signed with a secret from the environment, with a 60-minute expiry. The token carries the user id and role. On sensitive actions such as booking, the service re-loads the user from the database, so a deleted or changed account is honoured even while its token is still valid.

**Consequences**

* ✅ The app scales horizontally and the Docker setup stays simple.
* ✅ No session table, no CSRF protection needed.
* ❌ A token cannot be revoked before it expires. Mitigations: a short expiry and the database re-check described above.
* ❌ The secret must be kept safe. It is supplied only through environment variables and rotating it logs everyone out.

## 19\. Project structure

```text
.
├── .github/workflows/          # GitHub Actions CI
├── src/
│   ├── main/
│   │   ├── java/com/ionixx/eventticketbooking/
│   │   │   ├── controller/     # HTTP only, no business logic
│   │   │   ├── service/        # Business rules and transactions
│   │   │   ├── repository/     # Spring Data JPA repositories
│   │   │   ├── entity/         # JPA entities and enums
│   │   │   ├── dto/            # Request and response records
│   │   │   ├── exception/      # Custom exceptions and global handler
│   │   │   ├── filter/         # Request-id filter (MDC)
│   │   │   └── security/       # JWT, security config, access rules
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-docker.properties
│   │       ├── logback-spring.xml
│   │       └── db/changelog/   # Liquibase changelogs
│   └── test/                   # Unit, slice and integration tests
├── Dockerfile                  # Multi-stage build, non-root runtime
├── compose.yaml                # PostgreSQL + application
├── local\\\_properties.example    # Template for local.properties
├── build.gradle.kts
└── README.md
```

## 20\. Self-test checklist

Run these requests (with a valid token) to confirm everything works.

|Scenario|Expected|
|-|-|
|Create event with all valid fields|201 and a body with `id`|
|Create event with name `AB`|400, field `name`|
|Create event with category `COMEDY`|400, field `category`|
|Create event with a past `event\\\_date`|400, field `event\\\_date`|
|Create event with `total\\\_seats` 5|400, field `total\\\_seats`|
|Create event with two invalid fields|400 listing both|
|Create the same name and date twice|409 `DUPLICATE\\\_EVENT`|
|Body is not valid JSON|400 `INVALID\\\_REQUEST`|
|`GET /api/events/999`|404|
|`GET /api/events/abc`|400|
|Book with empty `seat\\\_numbers`, or 7 seats|400, field `seat\\\_numbers`|
|Book `\\\[5, 5]`|400, duplicate|
|Book seat 250 on a 200-seat event|400, `seat 250 does not exist for this event`|
|Book with email `priya@`|400, field `customer\\\_email`|
|Book an event whose date has passed|201, `REJECTED`, date reason|
|Book `\\\[12, 14, 16]` when 12 and 14 are taken|201, `REJECTED`, `Seats already booked: 12, 14`|
|Book seats a REJECTED booking asked for earlier|201, `CONFIRMED`|
|Same customer books 4 seats, then 3 more|second is 201 `REJECTED`, per-customer reason|
|4 seats, event 71 days away, base 1500|201, `1282.50` per seat, total `5130.00`|
|2 seats, event 10 days away, base 1000|201, `1000.00` per seat, total `2000.00`|
|`GET /api/events/{id}/seats` after bookings|booked and available lists correct and sorted|
|`GET /api/events/{id}/bookings?status=PENDING`|400|
|Summary for an event with no bookings|200, zeros, `LOW`|
|Call a protected endpoint with no token|401|
|Customer calls `POST /api/events`|403|
|Organiser opens another organiser's summary|403|

## 21\. Useful commands

```powershell
# Database
docker compose up -d postgres          # start only the database
docker compose up -d --build           # start database and app
docker compose ps                      # container status
docker compose logs -f app             # follow app logs
docker compose down                    # stop, keep data
docker compose down -v                 # stop and delete data

# Build and test
.\\\\gradlew.bat bootRun                  # run the app
.\\\\gradlew.bat clean test               # clean build and test
.\\\\gradlew.bat check                    # tests plus coverage rule
.\\\\gradlew.bat spotlessApply            # auto-format

# Git
git status
git diff --check                       # find whitespace errors
git switch -c feature/my-feature       # new feature branch
git add .
git commit -m "Describe the change"
git push -u origin feature/my-feature  # then open a Pull Request
```



