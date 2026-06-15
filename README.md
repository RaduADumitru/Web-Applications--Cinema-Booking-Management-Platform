# Web-Applications--Cinema-Booking-Management-Platform

The application is a Cinema Booking & Management System platform that lets users browse available movies, select screenings and book tickets, and lets administrators manage the platform's content (movies, rooms, schedule).
The system was initially designed as a monolithic application, later to be decomposed into a microservices-based architecture. The split is based on the application's main responsibilities: user management, movie management, and booking management.

# User Management
The system must allow user registration.
The system must allow user authentication.
The system must allow users to log out.
The system must manage roles.
The system must restrict access to certain features based on role.

# Movie Management
The system must allow viewing the list of movies.
The system must allow viewing the details of a movie.
Administrators must be able to: add movies, edit movies, delete movies.
The system must allow associating movies with screenings.
The system must allow searching and sorting movies.

# Screening and Room Management
The system must allow creating screenings for movies.
The system must allow associating a screening with a cinema room.
The system must manage the available seats in a room.
The system must allow viewing the available seats for a screening.

# Booking Management
Users must be able to: select a screening, select available seats, view their own bookings, cancel bookings.
The system must allow creating a booking.
The system must generate tickets for each booked seat.
The system must prevent double-booking of the same seat.

# Service Discovery (Eureka)

The microservices register themselves with a **Netflix Eureka** service registry
(`discovery-server`, host port **8761**) instead of using hardcoded inter-service
URLs. The gateway routes to services by name (`lb://user-service`, etc.) and the
Feign clients resolve their targets by service name through the registry.

## Eureka dashboard

With the stack running, open **http://localhost:8761**. The "Instances currently
registered with Eureka" table lists every running app:
`USER-SERVICE`, `CATALOG-SERVICE`, `BOOKING-SERVICE`, and `GATEWAY`. Stop a
container (`docker stop ms-catalog-service`) and refresh — it disappears from the
table; start it again and it re-registers automatically. This is the live proof
that services discover each other with no static configuration.

## What the logs show

- On startup each app logs its registration, e.g.
  `DiscoveryClient_CATALOG-SERVICE/... - registration status: 204`
  (`com.netflix.discovery` at INFO).
- On each inter-service call, the caller logs the name→instance selection, e.g.
  the gateway and booking-service log LoadBalancer choosing an instance for
  `catalog-service` (`org.springframework.cloud.loadbalancer` at DEBUG).

Because resolution is by service name, no URL changes are needed to move or scale
a service — the registry always reports its current location.

# Running the Microservices Stack (Docker)

The new microservices architecture lives in `microservices/` (a multi-module Maven
reactor) and runs via `docker-compose.microservices.yml`. It is independent of the
original monolith (`docker-compose.yml`) — run one or the other, not both at once
(the gateway and the monolith both use host port 8080).

## Prerequisites

- Docker + Docker Compose v2.
- A `.env` file at the repo root (copy from `.env.example`). It must include the
  three microservices DB-name vars:

  ```
  USER_DB_NAME=user_db
  CATALOG_DB_NAME=catalog_db
  BOOKING_DB_NAME=booking_db
  ```

  All other vars (`DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET_KEY`,
  `TMDB_API_KEY`, `BOOTSTRAP_OWNER_*`, `SECURITY_*`) are shared with the monolith.

## Start

```bash
docker compose -f docker-compose.microservices.yml up --build
```

First run is slow: the shared image compiles the whole reactor (`mvn install`), then
each service container compiles + boots its module. Watch the healthchecks; the
gateway only starts routing once `user-service`, `catalog-service`, and
`booking-service` report healthy.

## Ports

| URL | Component |
|---|---|
| http://localhost:8080/api/v1 | **API gateway** (what the frontend uses) |
| http://localhost:4200 | Angular client |
| http://localhost:8081/api/v1 | user-service (direct, debugging) |
| http://localhost:8082/api/v1 | catalog-service (direct) |
| http://localhost:8083/api/v1 | booking-service (direct) |

Internal `/internal/**` endpoints are **not** routed by the gateway — they are reachable
only over the Docker network (service-to-service Feign calls).

## Smoke test (through the gateway)

```bash
# Register (user-service via gateway). booking-service is up, so the welcome
# notification is delivered via the user->booking Feign call.
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Password123!","confirmPassword":"Password123!","email":"demo@example.com","firstName":"Demo","lastName":"User","phoneNumber":"+1234567890"}'

# Log in (sets jwt + refresh + XSRF-TOKEN cookies)
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Password123!"}'
```

Expect `201` then `200` with `Set-Cookie` headers — proving the gateway routes to
user-service and the cross-service calls work. The gateway logs every request
(`Gateway POST /api/v1/auth/login -> 200 OK (… ms)`).

## Stop

```bash
docker compose -f docker-compose.microservices.yml down        # keep data
docker compose -f docker-compose.microservices.yml down -v     # also drop DB volumes
```
