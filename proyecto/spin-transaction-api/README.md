# Transaction Execution API (Spin Challenge)

REST API for financial transaction execution. Validates business rules, calls an external provider, persists results in **PostgreSQL**, and exposes query endpoints with filters and pagination.

**Group:** `com.arayas`

## Stack

| Layer         | Choice                                         |
|---------------|------------------------------------------------|
| Runtime       | Java 17+, Spring Boot 4.0.6                    |
| Persistence   | PostgreSQL + JPA (Hibernate `ddl-auto=update`) |
| HTTP client   | `RestClient` with trace propagation            |
| Logs          | Logstash → **Elasticsearch** → **Kibana**      |
| Metrics       | Prometheus (`/actuator/prometheus`)            |
| Traces        | Jaeger OTLP (profile `observability`)          |
| Provider mock | `provider-mock` module (port 8084)             |
| Docker        | Docker desktop (https://docs.docker.com/desktop/setup/install/windows-install/)                              |

### Why PostgreSQL?

ACID guarantees, strong indexing for filtered queries, and alignment with production fintech stacks.

### Why no Liquibase?

Pragmatic MVP: Hibernate manages schema in dev. For production you would add Flyway/Liquibase or managed migrations.

## Ports

| Service | Port |
|---------|------|
| transaction-api | 8083 |
| provider-mock | 8084 |
| PostgreSQL | 5433 |
| Elasticsearch | 9200 |
| Logstash | 5000 |
| Kibana | 5601 |
| Jaeger UI | 16686 |
| Prometheus | 9090 |

## Quick start

### Option A — startup script (recommended)

```powershell
cd spin-transaction-api
.\startup.ps1
```

Linux/macOS/Git Bash:

```bash
cd spin-transaction-api
chmod +x startup.sh
./startup.sh
```

Flags: `-SkipDocker`, `-SkipObservability`, `-SkipBuild`, `-SkipApps`, `-ApiProfiles default`

### Option B — manual steps

#### 1. Infrastructure (Postgres + ELK + Jaeger + Prometheus)

```powershell
cd docker
docker compose up -d
```

Wait until Elasticsearch is healthy (~30s), then open **Kibana**: http://localhost:5601

**Kibana setup (first time):**

1. Menu → **Stack Management** → **Index Patterns**
2. Create pattern: `arayas-logs-*`
3. Time field: `@timestamp`
4. **Discover** → filter `service: "transaction-api"`

#### 2. Provider mock

```powershell
cd ../spin-provider-mock
mvn spring-boot:run
```

#### 3. Transaction API (with logs to Kibana)

```powershell
cd ../spin-transaction-api
mvn spring-boot:run "-Dspring-boot.run.profiles=observability"
```

Without ELK (console logs only):

```powershell
mvn spring-boot:run
```

#### 4. Try it

```powershell
curl -X POST http://localhost:8083/transactions `
  -H "Content-Type: application/json" `
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN","description":"Transfer received"}'

curl "http://localhost:8083/transactions?accountId=acc-123456"
```

Swagger UI (try endpoints interactively): http://localhost:8083/swagger-ui.html

## API reference

Base URL: `http://localhost:8083`

| Method | Path                   | Purpose                                              |
|--------|------------------------|------------------------------------------------------|
| `POST` | `/auth/register`       | Execute a new user and get a tocken                  |
| `POST` | `/auth/login`          | User login and get a tocken
| `POST` | `/transactions`        | Execute a new transaction (credit or debit)          |
| `GET`  | `/transactions`        | List stored transactions with filters and pagination |
| `GET`  | `/transactions/{id}`   | Get one transaction by UUID                          |
| `GET`  | `/actuator/health`     | Health check (ops / load balancers)                  |
| `GET`  | `/actuator/prometheus` | Metrics scrape endpoint                              ||

---
### POST `/auth/register` — Register user

**Purpose:** Creates a new user account and returns an authentication token. The token can then be used to access the protected transaction endpoints.

**Flow:**
1. Validate request body
2. Check that the username is not already registered
3. Hash the password before storing it
4. Persist the user in PostgreSQL
5. Generate a JWT token
6. Return the token

**Request body:**

```json
{
  "username": "john.doe",
  "email": "john.doe@email.com",
  "password": "Password123!"
}
```


| Field      | Required | Values               |
|------------|----------|----------------------|
| `username` | yes | 3-100 characters     |
| `email`    | yes | valid email          |
| `password` | yes | minimum 8 characters |

**Example:**

```powershell
curl -X POST http://localhost:8083/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"john.doe","email":"john.doe@email.com","password":"Password123!"}'
```
**Response (`201`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
The returned JWT must be included in the Authorization header when calling protected endpoints.

---
### POST `/auth/login` — Authenticate user

**Purpose:** Authenticates an existing user and returns a JWT token.

**Flow:**
1. Validate request body
2. Find the user by username
3. Verify the password
4. Generate a JWT token
5. Return the token

**Request body:**

```json
{
  "username": "john.doe",
  "password": "Password123!"
}
```

**Example:**

```powershell
curl -X POST http://localhost:8083/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"john.doe","email":"john.doe@email.com","password":"Password123!"}'
```

**Response(`201`):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Invalid credentials (`401`):**

```json
{
  "timestamp": "2026-08-14T18:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "path": "/auth/login"
}
```

---

### POST `/transactions` — Execute transaction

**Purpose:** Receives a credit or debit request, validates business rules **locally**, calls the external provider, **persists** the result (approved or rejected), and returns the saved record.

**Flow:**
1. Validate token
2. Validate request body (format)
3. Apply business rules (amount, currency, debit limit)
4. Call provider `POST /provider/v1/execute`
5. Save to PostgreSQL
6. Return `201 Created`

**Request body:**

```json
{
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Transfer received"
}
```

| Field | Required | Values |
|-------|----------|--------|
| `accountId` | yes | max 64 chars |
| `type` | yes | `CREDIT` \| `DEBIT` |
| `amount` | yes | decimal > 1.00 |
| `currency` | yes | `MXN` only (3 letters) |
| `description` | no | max 512 chars |

**Business rules (before provider call):**

- Amount must be **> 1.00**
- `DEBIT` cannot exceed **10,000.00** per operation (`CREDIT` has no max)
- Only **MXN** currency

**Example — successful credit (`201`):**

```powershell
curl -X POST http://localhost:8083/transactions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." `
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN","description":"Transfer received"}'
```

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Transfer received",
  "status": "EXECUTED",
  "providerTransactionId": "txn-789",
  "balanceAfter": 6500.00,
  "providerCode": null,
  "providerMessage": null,
  "executedAt": "2025-03-15T10:30:00Z",
  "createdAt": "2025-03-15T10:30:01Z"
}
```

**Example — provider rejection (`201`, persisted as rejected):**

Mock starts each account with balance **5000**. A debit above balance triggers `INSUFFICIENT_FUNDS`:

```powershell
curl -X POST http://localhost:8083/transactions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." `
  -d '{"accountId":"acc-123456","type":"DEBIT","amount":9000.00,"currency":"MXN","description":"Large withdrawal"}'
```

```json
{
  "id": "...",
  "accountId": "acc-123456",
  "type": "DEBIT",
  "amount": 9000.00,
  "currency": "MXN",
  "status": "REJECTED",
  "providerTransactionId": null,
  "balanceAfter": null,
  "providerCode": "INSUFFICIENT_FUNDS",
  "providerMessage": "The account does not have enough balance to complete the transaction",
  "executedAt": null,
  "createdAt": "..."
}
```

**Example — business rule violation (`400`, provider NOT called):**

```powershell
curl -X POST http://localhost:8083/transactions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." `
  -d '{"accountId":"acc-1","type":"DEBIT","amount":0.50,"currency":"MXN"}'
```

```json
{
  "timestamp": "2025-03-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Amount must be greater than 1.00",
  "path": "/transactions"
}
```

Other `400` examples: currency not MXN, debit > 10000.

---

### GET `/transactions` — List transactions

**Purpose:** Query the transaction history stored in PostgreSQL. Supports filters and pagination for high-volume accounts.

**Query parameters (all optional):**

| Param | Example | Description |
|-------|---------|-------------|
| `accountId` | `acc-123456` | Filter by account |
| `status` | `EXECUTED` | `EXECUTED` \| `REJECTED` |
| `type` | `CREDIT` | `CREDIT` \| `DEBIT` |
| `page` | `0` | Page number (0-based, default `0`) |
| `limit` | `20` | Page size (default `20`, max `100`) |

**Example:**

```powershell
curl "http://localhost:8083/transactions?accountId=acc-123456&status=EXECUTED&page=0&limit=10" ` 
    -H "Content-Type: application/json" `
    -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." `
```

**Response (`200`):**

```json
{
  "content": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "accountId": "acc-123456",
      "type": "CREDIT",
      "amount": 1500.00,
      "currency": "MXN",
      "description": "Transfer received",
      "status": "EXECUTED",
      "providerTransactionId": "txn-789",
      "balanceAfter": 6500.00,
      "providerCode": null,
      "providerMessage": null,
      "executedAt": "2025-03-15T10:30:00Z",
      "createdAt": "2025-03-15T10:30:01Z"
    }
  ],
  "page": 0,
  "limit": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Results are ordered by `createdAt` descending (newest first).

---

### GET `/transactions/{id}` — Get by ID

**Purpose:** Retrieve a single transaction by its UUID (e.g. after `POST` to confirm details, or for support/audit).

**Example:**

```powershell
curl http://localhost:8083/transactions/a1b2c3d4-e5f6-7890-abcd-ef1234567890 `
    -H "Content-Type: application/json" `
    -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." `
```

**Response (`200`):** same shape as one item in `POST` response.

**Not found (`404`):**

```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "Transaction not found: a1b2c3d4-...",
  "path": "/transactions/a1b2c3d4-..."
}
```

---

### External provider (mock) — `POST /provider/v1/execute`

**Purpose:** Simulates the external system that holds balances and approves/rejects transactions. **Not part of this API** — runs separately on port **8084** (`provider-mock` module).

Your service calls it internally; useful for manual testing:

```powershell
curl -X POST http://localhost:8084/provider/v1/execute `
  -H "Content-Type: application/json" `
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN"}'
```

The mock keeps an in-memory balance per `accountId` (default **5000.00**).

## Observability

| Signal | Where |
|--------|-------|
| Logs | Kibana → `arayas-logs-*` (profile `observability`) |
| Traces | Jaeger → http://localhost:16686 |
| Metrics | Prometheus → http://localhost:9090 |
| Health | `/actuator/health` |

Logs include `traceId` and `spanId` for correlation with Jaeger.

## Tests

```powershell
mvn test
```

Requires Docker for integration tests (Testcontainers PostgreSQL).

## Project layout

```
com.arayas.transaction/
├── adapters/web, persistence
├── application/service, port, model
└── infrastructure/provider, observability, exception

com.arayas.provider/   ← mock external provider
```

## AI usage

AI assisted with project scaffolding, observability configuration, code refactoring, clean code improvements, JUnit test scenarios, and adjustments to the startup.ps1 script.

All engineering decisions and implementation details were reviewed and validated by me.
