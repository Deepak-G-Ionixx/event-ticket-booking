# Event Ticket Booking Service

A Spring Boot REST API for managing events, booking seats, tracking seat availability, and viewing event summaries.

## 1. Technologies Used

- Java 21
- Spring Boot
- Gradle (Kotlin DSL)
- Spring Web
- Spring Data JPA
- Jakarta Bean Validation
- PostgreSQL
- Liquibase
- Spring Boot Actuator
- JUnit and Spring Boot Test
- Testcontainers
- Docker and Docker Compose

## 2. Prerequisites

Install the following before running the application:

- Java 21
- Docker Desktop
- Git
- IntelliJ IDEA (recommended)
- Postman (for API testing)
- DBeaver (optional, for viewing database tables)

Verify Java installation:

```powershell
java -version
```

Verify Docker is running:

```powershell
docker --version
docker compose version
```

## 3. Clone the Repository

```powershell
git clone https://github.com/Deepak-G-Ionixx/event-ticket-booking.git
cd event-ticket-booking
```

Make sure you are in the directory containing `build.gradle.kts`, `settings.gradle.kts`, and `gradlew.bat`.

## 4. Database Setup

This application uses PostgreSQL as its relational database.

### Step 1: Configure Docker Compose

Ensure your `docker-compose.yml` file is in the project root.

The following is an example configuration for a local development database:

```yaml
services:
  postgres:
    image: postgres:16
    container_name: event-ticket-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: eventticketdb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      TZ: Asia/Kolkata
      PGTZ: Asia/Kolkata
    ports:
      - "5433:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d eventticketdb"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

If you already have a working Compose file, keep your existing configuration and use the correct port in the application properties. Do not create a second PostgreSQL container unnecessarily.

### Step 2: Start PostgreSQL

Run from the directory containing `docker-compose.yml`:

```powershell
docker compose up -d
```

Check the container:

```powershell
docker compose ps
```

View PostgreSQL logs if needed:

```powershell
docker compose logs postgres
```

### Step 3: Configure the application

In `src/main/resources/application.properties`, configure the datasource to match your Docker Compose settings.

For the example above:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/eventticketdb
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true

spring.liquibase.enabled=true

management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=always
```

**Important:** These are example local-development credentials. Do not use real production credentials in source control. If your Compose file maps `5432:5432` instead, use port `5432` in the JDBC URL.

### Step 4: Database migrations

Liquibase manages the database schema through changelog files under:

```text
src/main/resources/db/changelog/
```

The master changelog is:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

When the application starts, Liquibase executes the configured migrations and creates or updates the database schema.

Hibernate uses `spring.jpa.hibernate.ddl-auto=validate` to validate the entity mappings against the database schema; it does not create the tables.

## 5. Run the Application

On Windows PowerShell, execute:

```powershell
.\gradlew.bat bootRun
```

The application will normally start at:

```text
http://localhost:8080
```

The root URL `/` may return `404 Not Found` if no root endpoint is implemented. Use the API endpoints documented below instead.

## 6. Health Check

After the application starts, open:

```text
http://localhost:8080/actuator/health
```

A healthy application typically returns:

```json
{
  "status": "UP"
}
```

## 7. API Endpoints

### Events

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/events` | Create an event |
| GET | `/api/events/{id}` | Retrieve an event by ID |

Example event request:

```json
{
  "name": "Chennai Tech Conference",
  "venue": "Chennai Trade Centre",
  "category": "TECH",
  "eventDate": "2027-01-15",
  "totalSeats": 200,
  "basePrice": 1500.00
}
```

Valid event categories:

- `MUSIC`
- `SPORTS`
- `TECH`
- `THEATRE`

### Bookings

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/bookings` | Create a booking |
| GET | `/api/bookings/{id}` | Retrieve a booking by ID |

Example booking request:

```json
{
  "event_id": 1,
  "customer_name": "Priya Kumar",
  "customer_email": "priya@example.com",
  "seat_numbers": [1, 2]
}
```

Replace the event ID with an existing event ID.

### Event bookings, seats and summary

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/events/{id}/bookings` | List bookings for an event |
| GET | `/api/events/{id}/bookings?status=CONFIRMED` | Filter bookings by status |
| GET | `/api/events/{id}/seats` | View booked and available seats |
| GET | `/api/events/{id}/summary` | View event statistics and revenue |

Supported booking status filters are `CONFIRMED` and `REJECTED`.

## 8. Run Tests

Run all tests:

```powershell
.\gradlew.bat test
```

Perform a clean build and run the tests:

```powershell
.\gradlew.bat clean test
```

The integration tests use Testcontainers to start a separate PostgreSQL test database. Docker Desktop must be running for these tests.

The test database is isolated from the local development database and is normally removed when the Testcontainers lifecycle ends.

## 9. Useful Docker Commands

Stop the development database:

```powershell
docker compose down
```

Stop the database and remove its container while preserving the named volume:

```powershell
docker compose down
```

Remove the database volume and its persisted data only when you intentionally want to reset the development database:

```powershell
docker compose down -v
```

**Warning:** The `-v` option deletes the Compose-managed database volume and its data.

## 10. Git Workflow

Check the current changes:

```powershell
git status
```

Check for whitespace errors:

```powershell
git diff --check
```

Create a feature branch:

```powershell
git switch -c feature/event-booking-apis
```

Stage and commit your changes:

```powershell
git add .
git commit -m "Complete event and booking APIs with integration tests"
```

Push the branch:

```powershell
git push -u origin feature/event-booking-apis
```

Open a Pull Request on GitHub with `main` as the base branch.

## 11. Project Structure

```text
src/
├── main/
│   ├── java/com/ionixx/eventticketbooking/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   ├── service/
│   │   └── EventTicketBookingApplication.java
│   └── resources/
│       ├── application.properties
│       └── db/changelog/
└── test/
    └── java/com/ionixx/eventticketbooking/
        └── integration/
            └── EventApiIntegrationTest.java
```

This is a representative structure. Keep the actual folder names and package locations used by your repository.

## 12. Error Handling

The API validates request data and returns appropriate HTTP status codes for common errors, including:

- `400 Bad Request` — invalid input or validation errors.
- `404 Not Found` — requested event or booking does not exist.
- `409 Conflict` — duplicate event or other defined conflict.
- `201 Created` — successful creation of an event or booking.

Exact response bodies depend on the endpoint and the application's exception-handling implementation.
