# AITSMSystem API Endpoints

This file lists backend endpoints, what they do, and POST payload examples for quick testing.

## Common Notes
- Base URL (local): `http://localhost:8070`
- Auth is header-based in this project:
  - `X-User-Id: <int>`
  - `X-User-Role: end-user | admin | superadmin`
- AI chat session continuity uses: `X-AI-Session-Id: <uuid/string>`

---

## System / App

### GET `/api/health`
- Returns health + environment info.

### GET `/metrics`
- Returns in-memory request counters and durations.

### GET `/docs`
- Placeholder endpoint for docs status.

---

## Authentication (`/api/auth`)

### POST `/api/auth/register`
- Registers a new end-user (email verification required).
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
- Logs in and returns token + user.
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

### POST `/api/users`
- Creates internal user (admin+).
- Example input:
```json
{
  "fullName": "Internal User",
  "email": "internal@company.com",
  "company": "Company",
  "department": "Ops",
  "password": "TempPass123!",
  "confirmPassword": "TempPass123!",
  "role": "END_USER",
  "emailVerified": true
}
```

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
- Deletes a user.

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

### POST `/api/monitoring/client-metrics`
- Client/agent upserts local host metrics.
- Example input:
```json
{
  "deviceName": "Client-PC-01",
  "ipAddress": "192.168.1.24",
  "department": "Finance",
  "assignedUser": "alice",
  "cpuUsage": 58,
  "memoryUsage": 72,
  "status": "Online"
}
```

### GET `/api/monitoring/host-telemetry`
- Host telemetry snapshot.

### GET `/api/monitoring/lan-devices`
- Aggregated LAN device view.

### GET `/api/monitoring/summary`
- Monitoring summary KPIs.

### POST `/api/monitoring/refresh-discovery`
- Refresh LAN discovery.
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

### GET `/api/ai/config`
- Returns AI config snapshot.

### POST `/api/ai/config`
- Updates AI config values.
- Example input:
```json
{ "baseUrl": "http://localhost:11434", "model": "llama3.1:8b" }
```

### GET `/api/ai/models`
- Returns model list from provider.

### POST `/api/ai/test`
- Tests provider connectivity.
- Example input:
```json
{ "baseUrl": "http://localhost:11434", "model": "llama3.1:8b" }
```
