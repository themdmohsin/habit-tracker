# CONTEXT.md - project memory between AI sessions

## Project goal
Course assignment on CI/CD. The app is a throwaway; only the pipeline matters, so keep the app minimal (no UI, DB, auth, or extra features).

Pipeline: code -> Git -> Jenkins CI -> Ansible -> Docker -> Kubernetes

## Chosen enhancements
- CI: automated tests run in Jenkins
- Deployment: liveness/readiness probes + ConfigMap + multi-stage Dockerfile

## Tech decisions so far
- Spring Boot 3.3.5, Java 17, Maven; app name `habit-tracker`
- Dependencies: spring-boot-starter-web, spring-boot-starter-actuator, spring-boot-starter-test
- In-memory storage (CopyOnWriteArrayList + AtomicLong); no database
- `<finalName>habit-tracker</finalName>` so the artifact is always `target/habit-tracker.jar`
- `server.port=${PORT:8080}`; only the `health` actuator endpoint is exposed (`/actuator/health`)
- Blank-name validation done manually in the controller (returns 400), no validation starter
- Exactly 3 JUnit 5 tests (MockMvc `@WebMvcTest`): empty list, POST creates, blank name -> 400
- Local env: Windows, JDK 17 (Temurin), Maven 3.10.0

## Endpoints
- `GET /habits` -> 200, JSON list
- `POST /habits` body `{"name":"..."}` -> 201 with `{id, name}`; blank -> 400
- `GET /actuator/health`

## Build / run commands
```
mvn clean package
java -jar target/habit-tracker.jar
curl http://localhost:8080/habits
curl -X POST -H "Content-Type: application/json" -d '{"name":"Read"}' http://localhost:8080/habits
curl http://localhost:8080/actuator/health
```
Custom port: set `PORT` env var (e.g. PowerShell: `$env:PORT=9090`).

## Progress log
- Session 1: Created the Spring Boot app (pom, controller, record, properties), 3 tests, .gitignore, README.md and this file. Verified `mvn clean package` and ran the jar with curl checks. Git NOT initialised yet. Next: git init + push, then Jenkins pipeline.
