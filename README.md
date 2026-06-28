# LondonMeet Backend

Spring Boot backend for the LondonMeet mini program and admin console.

## Requirements

- Java 17
- MySQL 8

## Local development

Prepare a MySQL 8 database named `london_meet`, then set the database values in `server/src/main/resources/application.yml` or through environment variables.

Run the backend from the server module:

```powershell
cd server
..\mvnw.cmd spring-boot:run
```

The API starts at `http://127.0.0.1:8080`.

## Build

```powershell
.\mvnw.cmd -DskipTests install
```

The deployable JAR is generated at:

```text
server/target/server-0.0.1-SNAPSHOT.jar
```

## Deployment

This repository is self-contained. Deploy only this repository and configure all production values as environment variables. Never commit `.env`, API secrets, database passwords, runtime uploads, logs, or Maven build output.

### Render

Use the backend repository root as Render's root directory.

Build command:

```bash
./mvnw -DskipTests package
```

Start command:

```bash
java -jar server/target/server-0.0.1-SNAPSHOT.jar
```

Logs should go to stdout/stderr only. Render collects them in the service Logs tab; do not write production logs into this repository.

Required environment variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
ADMIN_USERNAME
ADMIN_PASSWORD
WECHAT_APP_ID
WECHAT_APP_SECRET
CLOUDINARY_CLOUD_NAME
CLOUDINARY_UPLOAD_PRESET
GOOGLE_MAPS_API_KEY
CORS_ALLOWED_ORIGINS
```

Optional environment variables:

```text
PORT
JWT_EXPIRATION
DEFAULT_AVATAR_URL
```

Set `CORS_ALLOWED_ORIGINS` to the deployed admin console origin, for example
`https://your-admin-domain.onrender.com`.
