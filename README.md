# LondonMeet Backend

Spring Boot backend for the LondonMeet mini program and admin console.

## Requirements

- Java 17
- MySQL 8
- Redis 7
- RabbitMQ 4

## Local development

Start the infrastructure:

```powershell
docker compose up -d
```

Create a local `.env` from `.env.example`, then run:

```powershell
.\mvnw.cmd -f server\pom.xml spring-boot:run
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
