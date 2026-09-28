# AITSMSystem User Guide

This guide explains how to use the system as an end-user or admin.

## 1. Sign Up and Login
1. Open the application in your browser.
2. Go to **Sign Up** and fill in your details.
3. Complete email verification using the code.
4. Login with your credentials.

---

## 2. End-User Features

### 2.1 Create a Ticket
1. Open **Create Ticket**.
2. Enter title, description, priority, category, and optional device.
3. Submit ticket.

### 2.2 Track Tickets
1. Open **Tickets** page.
2. Review status and SLA indicators.
3. Update allowed status transitions when needed.

### 2.3 AI Troubleshooting Assistant
1. Open **AI Assistant**.
2. Type your issue and click **Send**.
3. Behavior:
   - If AI provider is healthy: you see one assistant chat reply.
   - If AI provider is unavailable/malformed: fallback troubleshooting panel appears.
4. Click **Create Ticket from Last Reply** to prefill ticket form from visible final output.

### 2.4 AI-Assisted Ticket Creation
1. Enter a ticket description with at least 10 characters.
2. Click **Analyze with AI**.
3. Review and edit the suggested title, description, category, and priority in the preview.
4. Review any knowledge recommendations or possible duplicate tickets.
5. Click **Apply reviewed suggestions** only when the proposed fields are accurate.
6. Continue editing as needed, then create the ticket normally.

### 2.5 Notifications
- Open notification areas in dashboard/shell to review updates.

### 2.5 Profile and Password
- Update your profile and change password from settings/profile pages.

---

## 3. Admin Features

### 3.1 User Management
- List users, adjust supported role state, reset passwords, approve email status, create internal accounts, delete users.
- Admin grant flow requires sensitive verification before granting admin rights.

### 3.2 Device Management
- Register LAN devices.
- Update/delete device records.
- Use IP lookup and sync-from-monitoring tools.

### 3.3 Monitoring
- Check host telemetry, LAN devices, summaries, CPU, and alerts.
- Refresh discovery when needed.

### 3.4 Knowledge Base
- Create/edit/delete knowledge articles.

### 3.5 Analytics
- View ticket trends and system health insights.

### 3.6 SLA
- View configured SLA policies.

### 3.7 AI Ticket Assistance
1. Open **Ticket Management** and select a ticket ID or title.
2. In **AI Ticket Assistance**, click **Generate assistance**.
3. Review the summary and checklist.
4. Edit the resolution draft before copying or using it. Generation never posts an update or changes ticket status automatically.

---

## 4. Practical Workflow (Recommended)
1. User experiences issue.
2. User tries AI Assistant first for quick troubleshooting.
3. If unresolved, user creates ticket (optionally from AI output).
4. Admin triages ticket and correlates with monitoring/device data.
5. Admin resolves and updates knowledge article if recurring issue.

---

## 5. Troubleshooting for Users

### I cannot log in
- Verify email was approved/verified.
- Reset password if needed.

### AI assistant shows fallback panel
- The hosted Gemini AI service may be temporarily unavailable, rate limited, or timed out.
- You can still create a ticket draft from fallback guidance.

### No monitoring data
- Confirm monitored devices are in LAN range and client metrics are being posted.

---

## 6. Keyboard/UX Tips
- In AI assistant input:
  - `Enter` = send
  - `Shift+Enter` = newline

---

## 7. Data & Safety Notes
- Do not include sensitive secrets in ticket descriptions.
- Use strong passwords.
- Admin actions are role-gated and sensitive operations require verification.
