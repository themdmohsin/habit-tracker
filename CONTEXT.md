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

## CI
- Maven Wrapper (Maven 3.9.9, `only-script` type: no wrapper jar committed, scripts download Maven on first run). Use `./mvnw` (Linux) or `mvnw.cmd` (Windows). `mvnw` has the executable bit in git and is forced to LF via `.gitattributes`.
- Jenkins runs natively on the user's Windows machine (not Docker). The `Jenkinsfile` uses a helper `mvnw(args)` that picks `sh ./mvnw -B ...` or `bat mvnw.cmd -B ...` via `isUnix()`, so no Maven install is needed in Jenkins (JDK 17 is required on the agent).
- Jenkinsfile (declarative, `agent any`): `pollSCM('* * * * *')` (polls every minute); options `timestamps()`, `disableConcurrentBuilds()`, `buildDiscarder(logRotator(numToKeepStr: '10'))`.
- Stages in order: Checkout (`checkout scm`) -> Test (`mvnw test`, runs BEFORE Build; the chosen CI enhancement) -> Build (`package -DskipTests`) -> Archive (`target/habit-tracker.jar`, fingerprinted).
- Top-level `post`: `always` -> `junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true`; `success`/`failure` -> echo.
- GitHub Actions `.github/workflows/ci.yml`: on push and pull_request, ubuntu-latest, checkout@v4, setup-java@v4 (temurin 17, maven cache), `./mvnw -B test`, `./mvnw -B package -DskipTests`, upload-artifact@v4 of `target/habit-tracker.jar`.
- `.gitattributes`: `* text=auto`, `*.sh text eol=lf`, `mvnw text eol=lf`.

## Remaining plan
1. Jenkins setup (manual, by the user): install/run Jenkins on Windows, create a Pipeline job from SCM pointing at the GitHub repo.
2. Ansible
3. Docker (multi-stage Dockerfile)
4. Kubernetes (probes + ConfigMap)

## Progress log
- Session 1: Created the Spring Boot app (pom, controller, record, properties), 3 tests, .gitignore, README.md and this file. Verified `mvn clean package` and ran the jar with curl checks. Git was not yet initialised at that point.
- Session 2 (Git step DONE): `git init -b main`; no remote added and nothing pushed (user will add the remote and push). Branches: `main` and `feature/habit-api` (kept, not deleted). Commits:
  - main: "Initial commit: project docs and gitignore" (.gitignore, README.md, CONTEXT.md)
  - feature/habit-api: "Add Maven build configuration"; "Add habit REST API with in-memory storage"; "Add unit tests for habit endpoints"; "Update CONTEXT.md with Git progress"
  - Merge strategy: `git merge --no-ff -m "Merge feature/habit-api into main"` so a visible merge commit exists on main.
  - Verified `mvn clean package` on merged main: 3 tests pass. `target/` is gitignored.
  - Next: add remote + push (user), then Jenkins CI pipeline (Jenkinsfile running `mvn clean package`).
- Session 3 (CI step DONE): branch `feature/ci-pipeline` (kept). Added Maven Wrapper 3.9.9, Jenkinsfile, GitHub Actions workflow, .gitattributes. Verified locally with `mvnw.cmd clean test` (3 tests pass) and `mvnw.cmd package -DskipTests` (jar built). Jenkins/GitHub Actions themselves not run here (YAML and Jenkinsfile reviewed by reading). Merged into main with `--no-ff`; nothing pushed. Next: user pushes, then manual Jenkins setup, Ansible, Docker, Kubernetes.
