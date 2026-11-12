# SmartTask

SmartTask is a task and project management application delivered through a web frontend and an Android mobile application. The repository also contains the ASP.NET Core API shared by both clients.

## Features found in the current code

- User registration and JWT-based login
- Projects, project members, invitations and project roles
- Task creation, editing, status and priority management
- Project messages and notifications
- User profile updates and password change flow
- Dashboard and statistics views
- Web and Android clients connected to the same REST API

These features describe the current source code; they are not a statement that every flow has been manually validated in this environment.

## Technology stack

| Application | Technologies |
| --- | --- |
| Frontend | Angular 19, TypeScript 5.7, SCSS, RxJS, Lucide Angular |
| Backend | ASP.NET Core / .NET 8, C#, Entity Framework Core 8, SQLite, JWT Bearer, Swagger/OpenAPI |
| Mobile | Native Android, Kotlin 2.0, Gradle, Android Gradle Plugin 8.13, Retrofit, Gson, Material Components, MPAndroidChart |

## Repository structure

```text
SmartTaskApp/
├── frontend/                 # Angular web application
├── backend/SmartTask.Api/    # ASP.NET Core REST API
└── Mobile/                   # Android application (Android Studio project)
```

The mobile directory is intentionally named `Mobile` to preserve the existing Android Studio project path and configuration.

## Prerequisites

- Node.js and npm for the frontend
- .NET 8 SDK for the backend
- Android Studio and an Android SDK for the mobile application

No dependency installation or application execution is performed by this repository preparation. Use the lockfile and Gradle wrapper supplied by the project where applicable.

## Run the backend

```powershell
cd backend/SmartTask.Api
dotnet restore
dotnet run
```

For local development, configure the JWT signing key and demo passwords outside the repository. With the .NET user-secrets tool, run these commands once from `backend/SmartTask.Api`:

```powershell
dotnet user-secrets set "Jwt:Key" "generate-a-long-local-development-value"
dotnet user-secrets set "SMARTTASK_ADMIN_PASSWORD" "choose-a-local-password"
dotnet user-secrets set "SMARTTASK_DEMO_PASSWORD" "choose-another-local-password"
```

The demo users and demo data are seeded only in the Development environment. If the two local demo passwords are not configured, the API starts without creating demo users.

The API uses a local SQLite database named `smarttask.db` and applies Entity Framework migrations at startup. Swagger is enabled in the development environment. The frontend configuration currently targets `http://localhost:5229/api`; update the local environment configuration if the API starts on another URL.

## Run the frontend

```powershell
cd frontend
npm ci
npm start
```

The Angular development server is normally available at `http://localhost:4200`.

## Run the mobile application

Open `Mobile/` in Android Studio, allow Gradle synchronization, select an emulator or device, and run the `app` configuration. `Mobile/local.properties` is machine-specific and must be created by Android Studio; it is intentionally not versioned.

The Android API URL is generated from the Gradle property `smartTaskApiBaseUrl`. The existing local address remains the default for compatibility. To use another development server, add a local property in `Mobile/gradle.properties` (do not commit it), for example:

```properties
smartTaskApiBaseUrl=http://10.0.2.2:5229/
```

## Configuration and security notes

Before publishing or deploying this project:

1. Keep the JWT signing key and connection settings in environment variables, user secrets, or a deployment secret store.
2. Keep demo credentials only in local user secrets; they are not stored in source code and are seeded only in Development.
3. Do not commit `appsettings.Development.json` if it later contains local secrets, the SQLite database, Android signing files, or `local.properties`.
4. Rotate any key that has already been shared outside the local workspace.

The source no longer contains the JWT signing key or demo passwords. Never commit local user-secrets files, database files, or deployment credentials.

## Verification status

The repository structure and configuration files were inspected without installing tools or launching the applications. The frontend, backend and mobile builds, integration behavior, Android emulator behavior, and production security configuration still require validation on machines with the required toolchains.
