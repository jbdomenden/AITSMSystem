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


## Default Superadmin Account

On startup, the application ensures a `superadmin` account exists for administrative access.

- Email: `superadmin@aitsm.local`
- Password: `SuperAdmin@123`

You can override these using environment variables:

- `SUPERADMIN_EMAIL`
- `SUPERADMIN_PASSWORD`
- `SUPERADMIN_NAME`
- `SUPERADMIN_COMPANY`
- `SUPERADMIN_DEPARTMENT`


## Email verification

End-user sign up now requires email verification before first login.

- `POST /api/auth/register` creates the account and generates a 6-digit verification code (15-minute expiry).
- `POST /api/auth/verify-email` verifies the code and returns an authenticated session payload.
- `POST /api/auth/resend-verification` regenerates the code for unverified users.

> In this environment, no SMTP server is configured, so the API returns `devVerificationCode` in the response for testing.

## Admin role management

Admins and superadmins can manage user roles from the Admin Dashboard (User Access Management) or via API: `PUT /api/users/{id}/role` with role `admin` or `end-user`.
