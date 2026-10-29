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

## Docker
- Multi-stage `Dockerfile` (chosen enhancement). Stage `build` (eclipse-temurin:17-jdk): copies mvnw, pom.xml, .mvn/ first, runs `sed -i 's/\r$//' mvnw` (CRLF safety) then `./mvnw -B -q dependency:go-offline` (cached layer), then copies src/ and runs `package -DskipTests`. Stage `runtime` (eclipse-temurin:17-jre): non-root user `app`, only the jar copied to `/app/app.jar`, `EXPOSE 8080`.
- `.dockerignore`: target/, .git/, .idea/, *.md, k8s/, ansible/.
- Tags: `habit-tracker:1.0` = old single-stage (copies prebuilt jar); `habit-tracker:2.0` = multi-stage. Both show 470MB disk / 130MB content in `docker images`: same JRE base, so the gain is a reproducible build without needing a local JDK/Maven, plus non-root user and layer caching, not image size.
- Smoke test of 2.0 on host port 8095: `/actuator/health` -> `{"status":"UP"}`, process runs as uid 999 (app). Test container removed.
- Host ports in use: 8080 (other app), 8081 (Jenkins), 8082 (test container), 8090 (Ansible target), 8095 (temporary smoke test).

## Kubernetes
- Cluster: Docker Desktop built-in single-node cluster; local images are visible, so `imagePullPolicy: IfNotPresent`, no registry.
- Manifests in `k8s/`: `configmap.yaml` (`habit-tracker-config`, PORT "8080"), `deployment.yaml` (3 replicas, label `app: habit-tracker`, image `habit-tracker:2.0`, PORT via `configMapKeyRef`, requests 200m/256Mi, limits 500m/512Mi), `service.yaml` (`habit-tracker-service`, NodePort, 80 -> 8080, nodePort 30080).
- Probes (enhancement): readiness `/actuator/health` initialDelay 20s period 5s; liveness same path initialDelay 40s period 10s failureThreshold 3.
- `kubectl apply --dry-run=client -f k8s/` passed for all three. Not applied for real yet.
- `k8s/README.md` lists the exact demo commands (apply, scale 5 and back to 3, describe, configmap, optional rollback demo).

## Remaining plan
1. Manual: apply the manifests, scale demo, screenshots (see `k8s/README.md`)
2. Report text
3. `repo_link.txt`
4. Resume lines
## Progress log
- Session 1: Created the Spring Boot app (pom, controller, record, properties), 3 tests, .gitignore, README.md and this file. Verified `mvn clean package` and ran the jar with curl checks. Git was not yet initialised at that point.
- Session 2 (Git step DONE): `git init -b main`; no remote added and nothing pushed (user will add the remote and push). Branches: `main` and `feature/habit-api` (kept, not deleted). Commits:
  - main: "Initial commit: project docs and gitignore" (.gitignore, README.md, CONTEXT.md)
  - feature/habit-api: "Add Maven build configuration"; "Add habit REST API with in-memory storage"; "Add unit tests for habit endpoints"; "Update CONTEXT.md with Git progress"
  - Merge strategy: `git merge --no-ff -m "Merge feature/habit-api into main"` so a visible merge commit exists on main.
  - Verified `mvn clean package` on merged main: 3 tests pass. `target/` is gitignored.
  - Next: add remote + push (user), then Jenkins CI pipeline (Jenkinsfile running `mvn clean package`).
- Session 3 (CI step DONE): branch `feature/ci-pipeline` (kept). Added Maven Wrapper 3.9.9, Jenkinsfile, GitHub Actions workflow, .gitattributes. Verified locally with `mvnw.cmd clean test` (3 tests pass) and `mvnw.cmd package -DskipTests` (jar built). Jenkins/GitHub Actions themselves not run here (YAML and Jenkinsfile reviewed by reading). Merged into main with `--no-ff`; nothing pushed. Next: user pushes, then manual Jenkins setup, Ansible, Docker, Kubernetes.
- Session 4 (Docker + Kubernetes DONE): branch `feature/kubernetes` (kept). Committed the previously untracked single-stage Dockerfile and ansible/ first. Upgraded Dockerfile to multi-stage, added .dockerignore, built habit-tracker:2.0 and smoke tested on 8095. Added k8s/ manifests (dry-run validated only) and k8s/README.md. Merged into main with `--no-ff`; nothing pushed. Next: user applies manifests and takes screenshots, then report, repo_link.txt, resume lines.
