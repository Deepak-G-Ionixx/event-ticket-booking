# Event Ticket Booking

Spring Boot REST API for creating and retrieving events. The project folder name remains `eventTicketBooking`.

## Technology stack

- Java 21
- Spring Boot 4.1.1
- Gradle
- Spring MVC REST API
- Spring Data JPA / Hibernate
- PostgreSQL 16 using Docker Compose
- Liquibase database migrations
- Jakarta Bean Validation
- Spring Boot Actuator

## Folder layout

```text
eventTicketBooking/
├── build.gradle
├── docker-compose.yaml
├── gradlew
├── gradlew.bat
├── settings.gradle
└── src/
    ├── main/
    │   ├── java/com/ionixx/eventticketbooking/
    │   │   ├── EventTicketBookingApplication.java
    │   │   ├── controller/EventController.java
    │   │   ├── dto/
    │   │   ├── entity/
    │   │   ├── exception/
    │   │   ├── repository/EventRepository.java
    │   │   └── service/EventService.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/changelog/
    └── test/
```

## Run on Windows

Open PowerShell in the `eventTicketBooking` folder.

1. Start PostgreSQL:

   ```powershell
   docker compose up -d
   docker compose ps
   ```

2. Run tests:

   ```powershell
   .\gradlew.bat clean test
   ```

3. Start Spring Boot:

   ```powershell
   .\gradlew.bat bootRun
   ```

4. Check health:

   `http://localhost:8080/actuator/health`

PostgreSQL is exposed on host port `5433` and container port `5432`, matching `application.properties`.

## API: create an event

`POST http://localhost:8080/api/events`

Header: `Content-Type: application/json`

```json
{
  "name": "Java Conference",
  "venue": "Chennai Trade Centre",
  "category": "TECH",
  "eventDate": "2026-11-15",
  "totalSeats": 500,
  "basePrice": 1499.00
}
```

A successful request returns `201 Created`, a `Location` header such as `/api/events/1`, and the saved event body. Use a date later than today's date.

Allowed categories: `MUSIC`, `SPORTS`, `TECH`, `THEATRE`.

## API: retrieve an event

`GET http://localhost:8080/api/events/1`

- `200 OK`: event exists
- `400 Bad Request`: ID is not a positive integer
- `404 Not Found`: positive ID does not exist

## Validation and duplicate rules

- Name and venue: required, 3–100 characters
- Category: required, one of the four enum values
- Event date: required and in the future
- Total seats: 10–10,000
- Base price: greater than zero, maximum 2 decimal places
- `(name, event_date)` must be unique
- Database constraints back up key validation rules; Liquibase manages the schema
- Hibernate is configured with `ddl-auto=validate`, not `update` or `create`

## Error response example

```json
{
  "timestamp": "2026-10-20T04:30:00Z",
  "status": 409,
  "error": "DUPLICATE_EVENT",
  "message": "An event with the same name and date already exists",
  "fieldErrors": {}
}
```

## Database notes

The original no-op Liquibase changeset is retained so databases that have already recorded it are not broken. A new changeset creates the `event` table. Never edit a Liquibase changeset that has already been applied to a shared database; add a new changeset instead.

The local database credentials are configured as `postgres` / `welcome` for development. Override them with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` environment variables when needed.


## Application logging

- Active structured JSON log file: `logs/event-ticket-booking.json` (relative to the directory from which the application is started).
- Rotated/compressed files: `logs/archive/` (up to 30 days, 10 MB per file, total archive cap 1 GB).
- Logs are also printed to the IntelliJ console.
- Each HTTP request receives an `X-Request-Id` if the caller did not provide a valid one. Successful event create/get operations add `eventLogId` to their log entries and the HTTP completion entry.
- Event log IDs use the database-generated event ID: `ETB%04d`, so ID 1 becomes `ETB0001`, ID 13 becomes `ETB0013`, and IDs above 9999 are not truncated.

On Windows PowerShell, from the project root:

```powershell
Select-String -Path .\logs\event-ticket-booking.json -Pattern 'ETB0001'
Get-ChildItem .\logs -Recurse -File | Select-String -Pattern 'ETB0001'
```

The second command searches uncompressed text files. Archived `.gz` files need to be extracted or searched with a gzip-aware tool.

Start with a clean Gradle build so stale files under `build/resources/main` are regenerated:

```powershell
.\gradlew.bat clean bootRun
```
