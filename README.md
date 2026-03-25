# ktor-aitsm

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                               | Description                                                 |
|----------------------------------------------------|-------------------------------------------------------------|
| [Routing](https://start.ktor.io/p/routing-default) | Allows to define structured routes and associated handlers. |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
|-----------------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```


## Runtime environment variables

Copy `.env.example` to `.env` and set values for your environment before startup.

The server now requires credentials and provider configuration via environment variables (no hard-coded defaults for secrets).

Required in all environments:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`

Required in production (`APP_ENV=production`):

- `SUPERADMIN_EMAIL`
- `SUPERADMIN_PASSWORD`

Optional:

- `SUPERADMIN_NAME`
- `SUPERADMIN_COMPANY`
- `SUPERADMIN_DEPARTMENT`
- `CORS_ALLOWED_ORIGINS`
- `AI_PROVIDER`
- `AI_OLLAMA_BASE_URL`
- `AI_OLLAMA_MODEL`
- `AI_TIMEOUT_MILLIS`


## Email verification

End-user sign up now requires email verification before first login.

- `POST /api/auth/register` creates the account and generates a 6-digit verification code (15-minute expiry).
- `POST /api/auth/verify-email` verifies the code and returns an authenticated session payload.
- `POST /api/auth/resend-verification` regenerates the code for unverified users.

> In this environment, no SMTP server is configured, so the API returns `devVerificationCode` in the response for testing.

## Admin role management

Admins and superadmins can manage user roles from the Admin Dashboard (User Access Management) or via API: `PUT /api/users/{id}/role` with role `admin` or `end-user`.


## LAN client metrics ingestion

Admins can see CPU analytics based on real LAN client reports.

- Clients post metrics to `POST /api/monitoring/client-metrics` from the same LAN only.
- The endpoint accepts device metadata + CPU/memory values and upserts by LAN IP.
- Admin analytics (`/api/monitoring/cpu`, `/api/analytics/system-health`) use these stored LAN metrics.

Example payload:

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

## Schema note

Primary keys remain integer-based in this release to minimize API breakage; UUID migration is planned as a staged change with backward-compatible ID translation.
