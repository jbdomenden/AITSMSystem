# AITSMSystem Project Documentation

## 1. Overview
AITSMSystem is a Kotlin/Ktor-based IT Service Management platform that provides:
- User onboarding and role-controlled access
- Ticket lifecycle management
- Device inventory and LAN monitoring
- Knowledge base and notifications
- SLA policy support
- AI troubleshooting assistant + AI-to-ticket draft flow

The backend serves both API routes and static frontend pages.

---

## 2. Architecture

### 2.1 Runtime stack
- **Language:** Kotlin
- **Server:** Ktor
- **Database access:** Exposed DSL + HikariCP
- **Database:** PostgreSQL
- **Frontend:** static HTML/CSS/JS served by Ktor

### 2.2 Boot flow
1. `Application.module()` initializes headers/logging/JSON/CORS/status handlers.
2. `DatabaseFactory.init()` connects to PostgreSQL and ensures tables/columns exist.
3. `ServiceContainer` builds repositories/services.
4. Seed actions run (`knowledgeRepo.seedDefaults()`, `slaService.seedDefaults()`).
5. Superadmin bootstrap runs if environment variables are provided (required in production).
6. Routes are registered and static frontend is served.

---

## 3. Core Components

### 3.1 Data layer
- **Tables** are defined in `DatabaseFactory.kt` (`users`, `tickets`, `devices`, etc.).
- Foreign keys use cascade rules for safer cleanup.
- Indexed fields are used for high-frequency filters/sorts.
- `ai_conversation_messages` persists AI chat context across restarts.

### 3.2 Repositories
Repositories encapsulate Exposed DB operations:
- `UserRepository`, `TicketRepository`, `DeviceRepository`, `KnowledgeRepository`, etc.
- Pagination is implemented with `limit/offset` and a `PagedResult<T>` wrapper.

### 3.3 Services
Services hold business logic and orchestration:
- `AuthService` for registration/login/user operations.
- `TicketService` for status transitions, audit hooks, role checks.
- `MonitoringService` for host/LAN telemetry.
- `AIChatService` for provider calls, fallback handling, and persistent AI history.

### 3.4 Routing
Each domain has a dedicated route file under `src/main/kotlin/backend/routes`.
Routes are composed in `Application.module()` and depend on service/repository abstractions.

---

## 4. Security and Access Model

### 4.1 Header-based caller identity
This project uses request headers for role checks:
- `X-User-Id`
- `X-User-Role`

### 4.2 Roles
`UserRole` enum:
- `SUPERADMIN`
- `ADMIN`
- `END_USER`

Role gates are enforced in route handlers via `requireRole(...)`.

### 4.3 Error contract
Errors are normalized through status pages and `ApiErrorResponse` (status code + message + docs reference).

---

## 5. Ticketing Domain

### 5.1 Ticket statuses
`TicketStatus` enum:
- `OPEN`, `PENDING`, `RESOLVED`, `CLOSED`

### 5.2 Ticket flow behavior
- End-users can create tickets.
- Status transitions are validated in service logic.
- SLA lookup contributes remaining/overdue calculations in repository mapping.

---

## 6. Monitoring Domain

Features include:
- Host telemetry capture from server host
- LAN peer discovery and merge with registered devices
- CPU/alerts summary endpoints for dashboards
- Device sync utilities

---

## 7. AI Assistant Flow

### 7.1 Provider interaction
`AIChatService` calls Ollama via provider abstraction with timeout protection.

### 7.2 Strict source-based rendering contract
AI responses are normalized into one DTO shape:
- `source: "ollama" | "fallback"`
- `reachable: boolean`
- `reply: string?`
- `fallback: object?`

Rules:
- Ollama success => `source=ollama`, `reply` only, `fallback=null`
- Provider fail/timeout/malformed/blank => `source=fallback`, `reply=null`, fallback object populated
- Never mixed content in one response

### 7.3 Conversation persistence
Per-session messages are persisted and reloaded from `ai_conversation_messages`.

### 7.4 Ticket drafting
Frontend can submit visible final AI output to `/api/ai/create-ticket-draft` and prefill the Create Ticket page.

---

## 8. Pagination Model
List endpoints use:
- Query params: `limit`, `offset`
- Response:
  - `data: []`
  - `meta: { totalCount, pageSize, offset, currentPage }`

This is applied to tickets/devices/knowledge list APIs.

---

## 9. Environment Configuration
Use `.env` (from `.env.example`) for runtime values.
Key variables:
- DB: `DB_URL`, `DB_USER`, `DB_PASSWORD`
- Production superadmin: `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`
- AI: `AI_PROVIDER`, `AI_OLLAMA_BASE_URL`, `AI_OLLAMA_MODEL`, `AI_TIMEOUT_MILLIS`

---

## 10. Frontend Structure
Static files under `src/main/resources/static/frontend`:
- Pages (`*.html`) for dashboards/features
- Feature JS modules in `js/`
- Shared styling in `css/styles.css`

The AI assistant frontend module is `js/ai-assistant.js`.

---

## 11. Observability
- `/metrics` provides lightweight in-memory request counters and durations.
- `CallLogging` and status-page handling support runtime diagnostics.

---

## 12. Testing
- Build/compile checks through Gradle.
- Health route test exists as baseline (`HealthRouteTest.kt`).
- Add additional repository/service/route integration tests to expand coverage.
