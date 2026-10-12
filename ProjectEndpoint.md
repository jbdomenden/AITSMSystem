# AITSMSystem API Endpoints

This file lists backend endpoints, what they do, and POST payload examples for quick testing.

## Common Notes
- Base URL (local): `http://localhost:8070`
- Authentication uses the server-issued `aitsm_session` HttpOnly cookie. State-changing browser requests also send `X-CSRF-Token` matching the session-bound `aitsm_csrf` cookie. Legacy identity headers are rejected.
- AI chat session continuity uses: `X-AI-Session-Id: <uuid/string>`

---

## System / App

### GET `/api/health`
- Public liveness probe; returns only `{ "status": "ok" }`.

### GET `/metrics`
- Returns bounded in-memory request counters and durations. Requires `SECURITY_MANAGE` permission.

### GET `/docs`
- Placeholder endpoint for docs status.

---

## Authentication (`/api/auth`)

### POST `/api/auth/register`
- Disabled; registration requires a single-use invitation.
- Example input:
```json
{
  "fullName": "Jane Doe",
  "email": "jane@company.com",
  "company": "Company",
  "department": "IT",
  "password": "StrongPass123!",
  "confirmPassword": "StrongPass123!",
  "eulaAccepted": true,
  "eulaVersion": "1.0"
}
```

### POST `/api/auth/verify-email`
- Verifies email code and returns auth response.
- Example input:
```json
{ "email": "jane@company.com", "code": "123456" }
```

### POST `/api/auth/resend-verification`
- Regenerates verification code.
- Example input:
```json
{ "email": "jane@company.com" }
```

### POST `/api/auth/login`
- Validates credentials and account state, then sets an opaque HttpOnly session cookie. Administrator sessions remain restricted until MFA is completed.
- Example input:
```json
{ "email": "jane@company.com", "password": "StrongPass123!" }
```

### POST `/api/auth/logout`
- Logout acknowledgement.
- Example input:
```json
{}
```

### POST `/api/auth/reauth`
- Verifies the current password or MFA and returns a five-minute, session-bound grant for one exact sensitive-action scope.
- Send it only in `X-Reauth-Token`; do not persist it in browser storage.
- Scopes: `admin-access`, `mfa-change`, `security-config`, `sensitive-export`, `account-delete`, `recovery-codes`.

### MFA, sessions, invitations, and security events
- MFA endpoints support status, enrollment, confirmation, login completion, recovery-code regeneration, and optional end-user disable.
- Sessions can be listed, individually revoked, or revoked together with logout-all.
- Invitation mutations require recent `admin-access` reauthentication; resending invalidates the previous link.
- `GET /api/auth/security-events` returns only the signed-in user's recent activity and omits source IPs.

---

## User Management (`/api/users`)

### GET `/api/users/me`
- Gets current signed-in user.

### PUT `/api/users/me`
- Updates own profile.
- Example input:
```json
{ "fullName": "Jane Doe", "company": "Company", "department": "Security" }
```

### PUT `/api/users/me/password`
- Changes own password.
- Example input:
```json
{
  "currentPassword": "StrongPass123!",
  "newPassword": "NewStrongPass123!",
  "confirmPassword": "NewStrongPass123!"
}
```

### GET `/api/users`
- Lists users (admin+).

### PUT `/api/users/{id}/role`
- Updates user role (admin flow constraints apply).
- Example input:
```json
{ "role": "END_USER" }
```

### PUT `/api/users/{id}/reset-password`
- Admin reset user password.
- Example input:
```json
{ "newPassword": "TempPass123!", "confirmPassword": "TempPass123!" }
```

### PUT `/api/users/{id}/email-approval`
- Approves/disables email verification state.
- Example input:
```json
{ "approved": true }
```

### DELETE `/api/users/{id}`
- Deletes a user and requires an `account-delete` reauthentication grant.

### POST `/api/users/admin/{id}/mfa-reset`
- Audited `SUPERADMIN` recovery for another administrator. Requires an `mfa-change` grant and revokes all target sessions.

### POST `/api/users/admin/eligibility`
- Checks if target email can be granted admin.
- Example input:
```json
{ "targetEmail": "target@company.com" }
```

### POST `/api/users/admin/verify`
- Verifies acting admin password before grant.
- Example input:
```json
{ "password": "AdminPassword123!" }
```

### POST `/api/users/admin/grant`
- Grants admin role using verification token.
- Example input:
```json
{ "targetEmail": "target@company.com", "verificationToken": "token-from-verify" }
```

---

## Tickets (`/api/tickets`)

### POST `/api/tickets`
- Creates ticket (end-user).
- Example input:
```json
{
  "title": "VPN not connecting",
  "description": "Cannot connect from home Wi-Fi",
  "priority": "High",
  "category": "Network",
  "deviceId": 1
}
```

### GET `/api/tickets?limit=20&offset=0`
- Lists tickets with pagination metadata.

### GET `/api/tickets/{id}`
- Gets one ticket.

### PUT `/api/tickets/{id}`
- Updates ticket content.
- Example input:
```json
{
  "title": "VPN still not connecting",
  "description": "Error 720 after update",
  "priority": "High",
  "category": "Network",
  "deviceId": 1
}
```

### PUT `/api/tickets/{id}/status`
- Updates ticket status.
- Example input:
```json
{ "status": "PENDING" }
```

### PUT `/api/tickets/{id}/resolution`
- Admin/superadmin-only endpoint that saves a human-reviewed resolution on an active ticket.
- Closed tickets must be reopened before their reviewed resolution can be changed.
- Example input:
```json
{ "resolution": "Verified the display driver update and confirmed normal startup." }
```

---

## Knowledge Base (`/api/knowledge`)

### GET `/api/knowledge?limit=20&offset=0`
- List knowledge articles (paginated).

### POST `/api/knowledge`
- Create article (admin+).
- Example input:
```json
{
  "title": "Reset corporate VPN client",
  "content": "Step 1...",
  "category": "Network"
}
```

### PUT `/api/knowledge/{id}`
- Update article.
- Example input:
```json
{
  "title": "Reset corporate VPN client (updated)",
  "content": "Updated steps...",
  "category": "Network"
}
```

### DELETE `/api/knowledge/{id}`
- Delete article.

---

## Devices (`/api/devices`)

### POST `/api/devices`
- Create monitored device (admin+).
- Example input:
```json
{
  "deviceName": "Finance-PC-01",
  "ipAddress": "192.168.1.120",
  "department": "Finance",
  "assignedUser": "alice",
  "status": "Online"
}
```

### GET `/api/devices?limit=20&offset=0`
- List devices with pagination + live status merge.

### GET `/api/devices/ip-lookup?ip=192.168.1.120`
- Lookup device suggestion by IP.

### POST `/api/devices/sync-from-monitoring`
- Sync discovered LAN peers into device registry.
- Example input:
```json
{}
```

### PUT `/api/devices/{id}`
- Update device.
- Example input:
```json
{
  "deviceName": "Finance-PC-01",
  "ipAddress": "192.168.1.120",
  "department": "Finance",
  "assignedUser": "alice",
  "status": "Online"
}
```

### DELETE `/api/devices/{id}`
- Delete device.

---

## Monitoring (`/api/monitoring`)

### POST `/api/monitoring/collectors`
- Admin-only enrollment. Returns a scoped raw token once; only its hash is stored.

### GET `/api/monitoring/collectors`
- Admin-only collector connectivity and last-seen states.

### DELETE `/api/monitoring/collectors/{id}`
- Revokes a collector token.

### GET `/api/monitoring/collector/commands`
- Collector bearer-token endpoint. Returns the next queued discovery request or HTTP 204.

### POST `/api/monitoring/collector/reports`
- Collector bearer-token endpoint for validated device discovery and telemetry reports. Server receipt time is authoritative.

### POST `/api/monitoring/client-metrics`
- Retired; returns JSON HTTP 410.

### GET `/api/monitoring/host-telemetry`
- Retired; returns JSON HTTP 410 because Cloud Run host data is not endpoint telemetry.

### GET `/api/monitoring/lan-devices`
- Aggregated LAN device view.

### GET `/api/monitoring/summary`
- Monitoring summary KPIs.

### POST `/api/monitoring/refresh-discovery`
- Queues discovery for an enrolled collector; Cloud Run never scans the LAN directly.
- Example input:
```json
{}
```

### GET `/api/monitoring/devices`
- Devices list (monitoring perspective).

### GET `/api/monitoring/cpu`
- CPU analytics data.

### GET `/api/monitoring/alerts`
- High-resource alert list.

---

## Analytics (`/api/analytics`)

### GET `/api/analytics/ticket-trends`
- Ticket trend insights (admin+).

### GET `/api/analytics/system-health`
- System health analytics (admin+).

---

## Notifications

### GET `/api/notifications`
- Returns user notifications.

---

## SLA

### GET `/api/sla`
- Returns configured SLA policies.

---

## AI Assistant

### POST `/api/ai/chat`
- Main AI chat endpoint with strict source-based response.
- Example input:
```json
{ "message": "Blue screen after startup" }
```

### POST `/api/ai-assistant/chat`
- Alias/alternate AI chat endpoint.
- Example input:
```json
{ "message": "Laptop is very slow" }
```

### POST `/api/ai/clear`
- Clears AI chat session context.
- Example input:
```json
{}
```

### POST `/api/ai/create-ticket-draft`
- Creates a ticket draft from visible AI output.
- Example input:
```json
{
  "issueSummary": "Blue screen after startup",
  "ticketDescription": "BSOD appears after login",
  "suggestedPriority": "High",
  "originalUserMessage": "blue screen pc",
  "ticketTitle": "BSOD on startup"
}
```

### POST `/api/ai/ticket-insights`
- Authenticated, rate-limited advisory endpoint used by **Analyze with AI** on ticket creation.
- Returns an editable Gemini suggestion, published knowledge recommendations, and deterministic possible-duplicate matches.
- Does not create or modify a ticket.
- Example input:
```json
{ "title": "Wi-Fi disconnects", "description": "My laptop disconnects every ten minutes." }
```

### POST `/api/ai/tickets/{id}/assist`
- Admin/superadmin-only, rate-limited endpoint.
- Returns a ticket summary, editable resolution draft, and troubleshooting checklist.
- Does not post an update, resolve the ticket, or change its status.

### GET `/api/ai/config`
- Returns AI config snapshot.

### POST `/api/ai/config`
- Updates the Gemini model. The API key is configured only on the server.
- Example input:
```json
{ "model": "gemini-3.8-flash" }
```

### POST `/api/ai/test`
- Tests the server-side Gemini connection. No request body or API key is accepted.
