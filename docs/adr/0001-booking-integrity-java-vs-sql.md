\# ADR 0001: Booking integrity — which rules live in Java and which in SQL



\- \*\*Status:\*\* Accepted

\- \*\*Date:\*\* 2026-10-29

\- \*\*Deciders:\*\* Event Ticket Booking team



\## Context



Two customers can press "Book" for the same seat at the same moment, and two

requests from one customer can race against the 6-seats-per-event limit. The

API also had to stop trusting identity sent by the client: an organiser could

create an event for another organiser, and a customer could put someone

else's email on a booking. Seats were stored only as a comma-separated string

(`booking.seat\_numbers`), which no unique index can protect per seat.



\## Alternatives considered



1\. \*\*Java only\*\* — check "seat already taken" with a SELECT, then INSERT.

&#x20;  Two transactions can both pass the SELECT before either commits, so this

&#x20;  is a check-then-act race. Rejected as the sole guard.

2\. \*\*Serialise everything\*\* (`SERIALIZABLE` isolation or locking the event row

&#x20;  exclusively for every booking). Correct, but every booking for an event

&#x20;  would queue behind the others, and serialisation failures need retries.

3\. \*\*SQL only\*\* — triggers or check constraints for every rule. Pricing,

&#x20;  discounts and friendly rejection reasons become hard to read and test.

4\. \*\*Split responsibilities (chosen).\*\*



\## Decision



\*\*The database guarantees what must never be violated, whatever the code does:\*\*

one row per reserved seat in `booking\_seat` with `UNIQUE (event\_id, seat\_number)`;

NOT NULL foreign keys `event.organiser\_id → users` and `booking.customer\_id → users`;

a composite FK `booking\_seat (booking\_id, event\_id) → booking (id, event\_id)` so a

seat can never point at a different event than its booking; CHECK constraints

for categories, seat ranges and status values. Migrations use new Liquibase

changesets and never guess owners for legacy rows.



\*\*The service (Java) owns everything that needs context or a good message:\*\*

identity from the verified JWT (never from the body), role and ownership

checks through owner-scoped queries (`findByIdAndOrganiserId`), seat range,

pricing and discounts, and readable rejection reasons. `createBooking` runs in

one `@Transactional` method; the customer row is locked (`FOR UPDATE`) to

serialise that customer's own bookings, and the event row is share-locked so

an organiser cannot shrink or delete it mid-booking. A unique-constraint

violation is translated into HTTP 409 and rolls the whole booking back.



\## Consequences



\- \*\*Security:\*\* two organisers or two customers with valid tokens are told

&#x20; apart by data ownership, not just roles. Missing event → 404, someone

&#x20; else's event or booking → 403 (a deliberate, documented policy).

\- \*\*Concurrency:\*\* proven by PostgreSQL integration tests: of N simultaneous

&#x20; requests for one seat exactly one is confirmed, and parallel requests from

&#x20; one customer never exceed 6 seats. Different customers do not block each other.

\- \*\*Trade-offs:\*\* the seat rule exists twice (friendly pre-check in Java,

&#x20; authoritative constraint in SQL). A loser of a race gets 409 instead of a

&#x20; stored REJECTED booking. `booking.seat\_numbers` is kept for history and the

&#x20; API response, so it is denormalised data that must stay in step with

&#x20; `booking\_seat`. The integrity tests need Docker (Testcontainers).



