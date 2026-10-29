# Component 1: Git, Maven and Jenkins CI with Automated Tests

Repository: https://github.com/themdmohsin/habit-tracker.git

## Abstract

This component builds a small continuous integration pipeline around a minimal Spring Boot REST API called Habit Tracker. The source is versioned in Git using feature branches merged with explicit merge commits. Maven (through the Maven Wrapper) builds the project, and a Jenkins declarative pipeline polls the GitHub repository every minute, runs the automated tests, builds the JAR and archives it. The chosen enhancement is running the automated tests inside the CI pipeline before the build. A GitHub Actions workflow with the same steps was added as an extra. The application itself is deliberately simple, because the pipeline is the subject of the assignment.

## Introduction

Continuous integration means every change to the code is built and tested automatically, so problems are found early. The goal of this component is to show that flow end to end: code is committed to Git, Jenkins notices the change, tests run, and a deployable artifact (`habit-tracker.jar`) is produced.

The application is a REST API with two endpoints for habits plus a health endpoint. It has no database, user interface or authentication. Habits are held in memory.

## Implementation

### Development Environment

| Item | Value |
|---|---|
| Operating system | Windows (Jenkins and the build run natively, not in Docker) |
| Java | JDK 17 (Temurin) |
| Framework | Spring Boot 3.3.5 (declared in `pom.xml`) |
| Build tool | Maven 3.10.0 installed locally; Maven Wrapper pinned to 3.9.9 for CI |
| Test framework | JUnit 5 with Spring MockMvc (`spring-boot-starter-test`) |
| CI server | Jenkins, running natively on Windows, port 8081 |
| Hosted CI (extra) | GitHub Actions, `ubuntu-latest` runner |
| Version control | Git, remote on GitHub |

The Jenkins version and Git version were not recorded in the project files, so they are not stated here. Add them by hand if the template asks for them.

### Code

**Maven build (`pom.xml`).** The project inherits from `spring-boot-starter-parent` 3.3.5 and uses Java 17. It has three dependencies: `spring-boot-starter-web`, `spring-boot-starter-actuator` and `spring-boot-starter-test` (test scope). `<finalName>habit-tracker</finalName>` makes the artifact always `target/habit-tracker.jar`, which keeps the Jenkins archive step simple.

**Application.** `HabitController` stores habits in a `CopyOnWriteArrayList` and generates ids with an `AtomicLong`:

```java
@GetMapping
public List<Habit> list() { return habits; }

@PostMapping
public ResponseEntity<Habit> create(@RequestBody Map<String, String> body) {
    String name = body == null ? null : body.get("name");
    if (name == null || name.isBlank()) {
        return ResponseEntity.badRequest().build();
    }
    Habit habit = new Habit(sequence.incrementAndGet(), name);
    habits.add(habit);
    return ResponseEntity.status(HttpStatus.CREATED).body(habit);
}
```

`GET /habits` returns the list. `POST /habits` with `{"name":"..."}` returns 201 and the new habit, or 400 if the name is missing or blank. `application.properties` sets `server.port=${PORT:8080}` (the port can be changed with the `PORT` environment variable) and exposes only the `health` actuator endpoint.

**Tests.** `HabitControllerTest` contains three JUnit 5 tests using `@WebMvcTest` and MockMvc:

1. `listIsInitiallyEmpty`: `GET /habits` returns 200 and an empty list.
2. `postCreatesHabit`: `POST /habits` returns 201 with id 1 and name "Read".
3. `blankNameReturnsBadRequest`: a blank name returns 400.

`@DirtiesContext(AFTER_EACH_TEST_METHOD)` reloads the context after each test so the in-memory list starts empty every time.

**Maven Wrapper.** The project uses the `only-script` wrapper type (Maven 3.9.9): no wrapper JAR is committed, and the scripts download Maven on first use. This means Jenkins and GitHub Actions need only a JDK, not a Maven installation. `.gitattributes` forces `mvnw` to use LF line endings so it works on Linux.

**Jenkinsfile.**

```groovy
def mvnw(String args) {
    if (isUnix()) {
        sh "./mvnw -B ${args}"
    } else {
        bat "mvnw.cmd -B ${args}"
    }
}

pipeline {
    agent any
    triggers { pollSCM('* * * * *') }
    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('Checkout') { steps { checkout scm } }
        stage('Test')     { steps { mvnw('test') } }
        stage('Build')    { steps { mvnw('package -DskipTests') } }
        stage('Archive')  {
            steps {
                archiveArtifacts artifacts: 'target/habit-tracker.jar', fingerprint: true
            }
        }
    }
    post {
        always { junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true }
        success { echo 'Pipeline succeeded: tests passed and habit-tracker.jar was archived.' }
        failure { echo 'Pipeline failed: check the Test or Build stage output above.' }
    }
}
```

Explanation: the helper function `mvnw` runs the Maven Wrapper with `sh` on Linux or `bat` on Windows, so the same Jenkinsfile works on both. `pollSCM('* * * * *')` makes Jenkins check GitHub every minute and start a build only when there is a new commit. `disableConcurrentBuilds` prevents overlapping builds and `buildDiscarder` keeps only the last 10. The stages run in order: Checkout, Test, Build, Archive. Because Test comes before Build, a failing test stops the pipeline before any JAR is produced. The `junit` step in `post` publishes the Surefire XML reports so Jenkins shows test results.

**GitHub Actions workflow (`.github/workflows/ci.yml`).**

```yaml
on:
  push:
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: maven }
      - name: Test
        run: ./mvnw -B test
      - name: Build
        run: ./mvnw -B package -DskipTests
      - uses: actions/upload-artifact@v4
        with: { name: habit-tracker, path: target/habit-tracker.jar }
```

Explanation: on every push and pull request GitHub runs the same sequence (test, then build) on a hosted Ubuntu runner with Temurin JDK 17 and a Maven dependency cache, then uploads the JAR as an artifact.

**Git workflow.** Work was done on feature branches (`feature/habit-api`, `feature/ci-pipeline`, and later ones), each merged into `main` with `git merge --no-ff` so each merge appears as its own commit in the history graph. Feature branches were kept rather than deleted.

### Description

1. A developer commits and pushes to GitHub.
2. Jenkins (Windows, port 8081) polls the repository every minute. The job is a Pipeline job that reads the `Jenkinsfile` from SCM.
3. On a new commit, Jenkins checks out the code, runs `mvnw test`, then `mvnw package -DskipTests`, and archives `target/habit-tracker.jar`.
4. Test results are published from the Surefire reports and shown in the Stage View.
5. Independently, GitHub Actions runs the same test and build steps on each push.

## Enhancement chosen and why

**Automated tests in the CI pipeline (main enhancement).** The pipeline has a dedicated Test stage that runs before Build. It was chosen because a CI pipeline that only compiles proves little; running tests on every change catches regressions automatically and stops a broken change from producing an artifact. Test results are also published to Jenkins through the `junit` step, so the pass/fail result is visible per build.

**GitHub Actions (extra).** The same test and build steps run on GitHub's own runners. This was added as a second, independent confirmation that the project builds from a clean checkout on Linux, without depending on the local Jenkins machine.

## Results

[Figure 1: Git history graph showing feature branches and merge commits (`git log --graph --oneline --all --decorate`), proves the branch and merge workflow with `--no-ff` merge commits.]

The graph shows each feature branch diverging from `main` and returning through a visible merge commit, which demonstrates version control with a proper branching workflow.

[Figure 2: Jenkins job configuration page (Pipeline script from SCM, repository URL, Poll SCM schedule), proves the job is wired to the GitHub repository and reads the Jenkinsfile from it.]

The job is configured as a Pipeline from SCM pointing at the GitHub repository, so the pipeline definition lives in version control.

[Figure 3: The `Jenkinsfile` in the repository (GitHub or editor view), proves the pipeline is defined as code and stored with the project.]

This shows the stages Checkout, Test, Build and Archive as described above.

[Figure 4: Jenkins console output of a successful build, ending in `Finished: SUCCESS`, proves the whole pipeline ran without errors.]

The console shows Maven running the tests, packaging the JAR, and archiving the artifact.

[Figure 5: Jenkins Stage View with the Test stage before the Build stage, plus the test result trend, proves tests run before the build and that the three tests pass.]

The Stage View shows the stage order, and the test results page shows 3 tests passing from `HabitControllerTest`.

[Figure 6: Build page showing the archived artifact `habit-tracker.jar`, proves the pipeline produces and stores the deployable output.]

The artifact is fingerprinted and downloadable from the build page, and the same JAR is later used by the Ansible and Docker steps.

[Figure 7: A build page showing "Started by an SCM change", proves polling triggers builds automatically on new commits without manual action.]

After a commit was pushed to GitHub, Jenkins detected the change within its one-minute polling interval and started a build on its own.

[Figure 8: GitHub Actions run for the repository (green check on the CI workflow), proves the same test and build steps pass on a hosted runner.]

The run shows the Test, Build and artifact upload steps completed on `ubuntu-latest`.

## Conclusion

The component delivers a working CI pipeline: code is versioned in Git with a clear branch and merge history, built with the Maven Wrapper, tested automatically on every change, and packaged into an archived JAR by Jenkins. Running tests before the build means a failing test blocks the artifact. The GitHub Actions workflow adds a second CI path that needs no local infrastructure.

Limitations: Jenkins runs on a single local Windows machine and uses polling rather than a webhook, so builds can start up to a minute after a push. The tests are three controller-level tests; there are no integration tests. The pipeline stops at producing the JAR; deployment is handled in Component 2.

## References

1. Spring Boot Reference Documentation. https://docs.spring.io/spring-boot/index.html
2. Apache Maven Documentation. https://maven.apache.org/guides/index.html
3. Apache Maven Wrapper. https://maven.apache.org/wrapper/
4. Jenkins User Documentation: Pipeline. https://www.jenkins.io/doc/book/pipeline/
5. Jenkins Pipeline Syntax (Declarative Pipeline). https://www.jenkins.io/doc/book/pipeline/syntax/
6. GitHub Actions Documentation. https://docs.github.com/en/actions
7. Git Documentation. https://git-scm.com/doc
