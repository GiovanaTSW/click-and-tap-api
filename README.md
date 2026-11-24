# Click and Tap API

Backend of **Click and Tap**: customizable physical products (plates, keychains, stickers, NFC/QR tags) linked to configurable **digital experiences** (review pages, digital menus, profiles, event badges, pet IDs, gifts). Each experience has a permanent public URL (a *slug*) that opens when someone scans the tag.

REST API for web and mobile clients. Also the backend of the *Front-End Development and Mobile Applications* course project.

## Stack

Java 21 · Spring Boot 4.2 (Spring Security 7, Hibernate 7, Jackson 3) · PostgreSQL 18 · Flyway · MapStruct · JWT (jjwt) · springdoc OpenAPI · JUnit 5, Mockito, Testcontainers · Docker · GitHub Actions

## Quick start

### Option A: Docker Compose (API + PostgreSQL)

Requires Docker Desktop.

```powershell
Copy-Item .env.example .env
```

Open `.env` and fill in the empty values: `POSTGRES_PASSWORD`, `JWT_SECRET` (at least 32 random characters) and, to get an administrator, `ADMIN_EMAIL` and `ADMIN_PASSWORD`. Set `SWAGGER_ENABLED=true` if you want Swagger UI.

```powershell
docker compose up --build
```

The API is at <http://localhost:8088>. `docker compose down` stops it; add `-v` to also delete the database.

### Option B: run from IntelliJ (development)

Requires JDK 21 and a local PostgreSQL with an empty database called `clickandtap`.

1. Copy `src/main/resources/application-dev.properties.example` to `application-dev.properties` (it is git-ignored) and fill in your values.
2. Run `ApiApplication`. The `dev` profile is used when no profile is set.
3. Flyway creates the tables on startup and `DataInitializer` creates the administrator from `app.admin.*` (and sample products if `app.seed-sample-data=true`).

Swagger UI: <http://localhost:8088/swagger-ui.html>. Click **Authorize** and paste a token from `POST /api/auth/login`.

## Configuration

Production (`pdn` profile, used by the Docker image) reads everything from environment variables. Nothing secret lives in the repository.

| Variable | Required | Description |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | JDBC connection. Compose sets them for you. |
| `JWT_SECRET` | yes | At least 32 random characters. |
| `CORS_ALLOWED_ORIGINS` | yes | Comma-separated browser origins, e.g. `https://app.clickandtap.mx`. |
| `JWT_EXPIRATION_MS` | no | Token lifetime, default `3600000` (1 hour). |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | no | Administrator created at startup if the e-mail does not exist. |
| `SWAGGER_ENABLED` | no | `true` publishes `/swagger-ui.html` and `/v3/api-docs`. Default `false`. |
| `PORT` | no | Port inside the container, default `8080`. |

The app does not start if a required variable is missing. In production, put a reverse proxy with HTTPS in front of it; the profile trusts `X-Forwarded-*` headers so rate limiting sees the real client IP.

## API overview

All endpoints are under `/api`. Send `Authorization: Bearer <token>` where a token is required. Interactive documentation is in Swagger UI.

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create a customer account and get a token |
| POST | `/api/auth/login` | public | Get a token |
| GET | `/api/auth/me` | token | Current user |
| GET | `/api/products` | public | Catalog. Params: `type`, `page`, `size`, `sort` |
| GET | `/api/products/{id}` | public | Product detail |
| POST | `/api/products` | admin | Create a product |
| PUT | `/api/products/{id}` | admin | Edit product data (never the stock) |
| PATCH | `/api/products/{id}/stock` | admin | Add or remove stock: `{"delta": 10}` |
| DELETE | `/api/products/{id}` | admin | Deactivate the product (soft delete) |
| GET | `/api/admin/products` | admin | All products, including inactive |
| GET | `/api/admin/products/{id}` | admin | One product with exact stock |
| POST | `/api/orders` | token | Place an order |
| GET | `/api/orders` | token | My orders, newest first |
| GET | `/api/orders/{id}` | owner or admin | Order detail |
| GET | `/api/admin/orders` | admin | All orders. Params: `status`, `page`, `size`, `sort` |
| PATCH | `/api/admin/orders/{id}/status` | admin | Change order status |
| POST | `/api/experiences` | token | Create a digital experience |
| GET | `/api/experiences` | token | My experiences |
| GET | `/api/experiences/{id}` | owner or admin | Experience detail |
| PUT | `/api/experiences/{id}` | owner or admin | Edit title, description, links and active flag |
| DELETE | `/api/experiences/{id}` | owner or admin | Delete |
| GET | `/api/public/experiences/{slug}` | public | The page opened by the NFC/QR tag |
| GET | `/actuator/health` | public | Health check |

### Conventions

- **Errors** always look like `{ "status": 400, "message": "..." }`. Codes: `400` invalid input, `401` missing or invalid token (or wrong credentials), `403` not allowed, `404` not found, `405` wrong method, `409` conflict, `429` too many attempts (see the `Retry-After` header), `500` unexpected error (details stay in the server log).
- **Pagination**: `?page=0&size=20&sort=price,desc`. `page` starts at 0, `size` is 1–100. Lists answer `{ items, page, size, totalItems, totalPages }`. Sortable fields: products `id, name, price, type`; orders `id, createdAt, total, status`. Anything else is a `400`.
- **Dates** are ISO-8601 UTC instants, e.g. `2026-11-24T02:13:01.123Z`.
- **Money** is `price`/`total`/`unitPrice` in MXN with two decimals. Totals and prices are always computed by the server.

### Order lifecycle

```
PENDING -> PAID -> IN_PRODUCTION -> SHIPPED -> DELIVERED
   \         \
    +---------+--> CANCELLED   (only before production starts)
```

Placing an order takes stock; cancelling gives it back exactly once. Invalid transitions answer `400`.

### Public experiences

An experience's `slug` is generated from its title and **never changes**, because it may be printed on a physical tag. `GET /api/public/experiences/{slug}` returns only active experiences and active links, without ids or owner data. Link URLs must start with `http://`, `https://`, `mailto:` or `tel:`.

## Security notes

- Passwords are hashed with BCrypt (at most 72 bytes). Tokens are stateless HS256 JWTs.
- Login: 5 failed attempts per IP + e-mail, or 20 per IP, in 15 minutes block further attempts (`429`). Registration: 10 per IP per hour. The limiter is in memory, which is enough for one instance.
- Customers cannot choose their role. Administrators come from `DataInitializer` or the database.
- Known limits: tokens cannot be revoked before they expire (default 1 hour) and there is no refresh token yet.

## Tests

```powershell
.\gradlew.bat test
```

- **Unit tests** (JUnit 5 + Mockito): services, order lifecycle, pagination, rate limiter.
- **Integration tests** (Testcontainers): the whole application against a real PostgreSQL started in Docker: auth, security (401/403, forged tokens), products, orders, experiences, schema constraints, and concurrency (many simultaneous orders never oversell stock). **Docker must be running.** They do not need `application-dev.properties`.

If downloading the PostgreSQL image is slow, run the tests with an image you already have: `$env:TEST_POSTGRES_IMAGE = 'postgres:16'`.

## Database migrations

Flyway applies `src/main/resources/db/migration` on startup and Hibernate only validates (`ddl-auto=validate`).

| Migration | Content |
|---|---|
| `V1__init_schema.sql` | Tables |
| `V2__constraints_and_indexes.sql` | `CHECK` constraints (stock and price never negative, allowed enum values) and indexes |
| `V3__timestamptz_and_order_item_product_name.sql` | `TIMESTAMPTZ` columns; product name saved on each order item |

Never edit a migration that has been applied: add a new `Vn__description.sql`.

## Project layout

```
mx.edu.clickandtap.api
├── domain/               pure business model, services and ports (no web or persistence code)
│   ├── service/          business rules
│   ├── repository/       interfaces implemented by the persistence layer
│   ├── pagination/       PageQuery / PageResult
│   └── exception/        errors mapped to HTTP codes by the web layer
├── persistence/          JPA entities, Spring Data repositories, MapStruct mappers
└── web/                  controllers, DTOs, security (JWT, rate limiting), configuration
```

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:

1. **Build and test**: `./gradlew build` with all unit and integration tests.
2. **Docker**: builds the image, starts the stack with `docker compose` using throwaway secrets and runs a smoke test (health check, catalog, and `401` on a protected endpoint).

## Conventions

Conventional commits in English: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`.
