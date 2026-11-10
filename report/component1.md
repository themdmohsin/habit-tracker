# Component 1: Git, Maven and Jenkins CI with Automated Tests

Repository: https://github.com/themdmohsin/habit-tracker.git

## Abstract

This component builds a continuous integration (CI) pipeline around a small Spring Boot application called habit-tracker. The code is versioned in Git using several branches and merges, including one GitHub pull request (`feature/ui`). Maven (through the Maven Wrapper) builds the project. A Jenkins declarative pipeline, running natively on Windows on port 8081, polls the GitHub repository every minute (`pollSCM`) and runs the stages Checkout, Test, Build and Archive. The chosen enhancement is running the 10 automated JUnit tests in a dedicated Test stage before the build. A GitHub Actions workflow that builds and tests on every push runs alongside Jenkins. No failing build was demonstrated in this project; only successful runs are shown.

## Introduction

Continuous integration means every change to the code is built and tested automatically, so problems are found early. This component shows that flow: code is committed to Git, Jenkins notices the change, the tests run, and a JAR (`habit-tracker.jar`) is produced and archived.

The application is a Spring Boot 3.3.5 REST API (Java 17) with a single-page UI served at `/`. Its endpoints are:

- `GET /habits` and `POST /habits`
- `POST /habits/{id}/check-in`
- `DELETE /habits/{id}`
- `GET /info`
- `GET /actuator/health`

Habit data is kept in memory. There is no database and no authentication. The application is deliberately simple, because the pipeline is the subject of the assignment.

## Implementation

### Development Environment

| Item | Value |
|---|---|
| Operating system | Windows (Jenkins and the build run natively, not in Docker) |
| Java | JDK 17 (Temurin) |
| Framework | Spring Boot 3.3.5 (declared in `pom.xml`) |
| Build tool | Maven Wrapper (Maven 3.9.9 downloaded on first use); Maven 3.10.0 is also installed locally |
| Test framework | JUnit 5 with Spring MockMvc (`spring-boot-starter-test`) |
| CI server | Jenkins, running natively on Windows, port 8081 |
| Hosted CI | GitHub Actions, `ubuntu-latest` runner |
| Version control | Git, remote on GitHub |

The Jenkins and Git versions are not recorded in the repository, so they are not stated here.

### Code

**Maven build (`pom.xml`).** The project inherits from `spring-boot-starter-parent` 3.3.5 and sets Java 17. It has three dependencies: `spring-boot-starter-web`, `spring-boot-starter-actuator` and `spring-boot-starter-test` (test scope). `<finalName>habit-tracker</finalName>` makes the artifact always `target/habit-tracker.jar`, which keeps the Jenkins archive step simple.

**Application.** `HabitController` keeps habits in a `CopyOnWriteArrayList` and creates ids with an `AtomicLong`:

```java
@PostMapping
public ResponseEntity<HabitView> create(@RequestBody Map<String, String> body) {
    String name = body == null ? null : body.get("name");
    if (name == null || name.isBlank()) {
        return ResponseEntity.badRequest().build();
    }
    ...
    return ResponseEntity.status(HttpStatus.CREATED).body(view(habit, today));
}
```

`POST /habits` returns 201 and the new habit, or 400 if the name is blank. `POST /habits/{id}/check-in` marks a habit done for today (and returns 404 for an unknown id). `DELETE /habits/{id}` returns 204, or 404 for an unknown id. `GET /info` returns the pod (host) name and the version. `application.properties` sets `server.port=${PORT:8080}` so the port can be changed with the `PORT` environment variable, and exposes only the `health` actuator endpoint.

**Tests.** `HabitControllerTest` contains 10 JUnit 5 tests that use `@WebMvcTest` and MockMvc:

1. `listIsInitiallyEmpty`
2. `postCreatesHabit`
3. `blankNameReturnsBadRequest`
4. `checkInMarksDoneTodayWithStreakOne`
5. `checkInTwiceSameDayKeepsStreakOne`
6. `deleteReturns204ThenNotFound`
7. `checkInUnknownIdReturns404`
8. `defaultCategoryIsGeneral`
9. `infoReturnsPodAndVersion`
10. `streakCountsConsecutiveDaysEndingTodayOrYesterday`

```java
@WebMvcTest({HabitController.class, InfoController.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HabitControllerTest {
```

`@DirtiesContext` reloads the context after each test, so the in-memory list starts empty every time.

**Maven Wrapper.** The wrapper is the `only-script` type (Maven 3.9.9): no wrapper JAR is committed, and the scripts download Maven on first use. Jenkins and GitHub Actions therefore need only a JDK. `.gitattributes` forces `mvnw` to use LF line endings so it works on Linux.

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
        ...
    }
}
```

(The real file lays the stages out over more lines and also has `success` and `failure` echo messages.)

Explanation: the helper `mvnw` runs the Maven Wrapper with `bat` on Windows or `sh` on Linux. `pollSCM('* * * * *')` makes Jenkins check GitHub every minute and start a build when there is a new commit. `disableConcurrentBuilds` stops overlapping builds and `buildDiscarder` keeps the last 10. The stages run in order: Checkout, Test, Build, Archive. Because Test comes before Build, a failing test would stop the pipeline before the JAR is built. The `junit` step publishes the Surefire XML reports so Jenkins shows the test results.

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
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - name: Test
        run: ./mvnw -B test
      - name: Build
        run: ./mvnw -B package -DskipTests
      - uses: actions/upload-artifact@v4
        with:
          name: habit-tracker
          path: target/habit-tracker.jar
```

Explanation: on every push and pull request, GitHub runs the same sequence (test, then build) on a hosted Ubuntu runner with Temurin JDK 17 and a Maven cache, then uploads the JAR as an artifact.

**Git workflow.** Work was done on feature branches (`feature/habit-api`, `feature/ci-pipeline`, `feature/kubernetes`, `feature/report`, `feature/ui`, `feature/ansible-fix`) and merged into `main`. Most merges used `git merge --no-ff`, so each appears as its own merge commit. `feature/ui` was merged through a GitHub pull request (#1), and `feature/ansible-fix` was merged afterwards. The feature branches were kept, not deleted.

### Description

1. A developer commits and pushes to GitHub.
2. Jenkins (Windows, port 8081) polls the repository every minute. The job is a Pipeline job that reads the `Jenkinsfile` from SCM.
3. When there is a new commit, Jenkins checks out the code, runs `mvnw test`, then `mvnw package -DskipTests`, and archives `target/habit-tracker.jar`.
4. Test results are published from the Surefire reports.
5. Separately, GitHub Actions runs the same test and build steps on every push.

## Enhancement chosen and why

**Automated tests in CI (main enhancement).** The pipeline has a dedicated Test stage that runs before Build. A pipeline that only compiles proves little. Running the 10 tests on every change catches regressions automatically, and a failing test would stop the pipeline before the JAR is built. Test results are published to Jenkins with the `junit` step. (This blocking behaviour comes from the pipeline order; no failing build was actually run during this project.)

**GitHub Actions alongside Jenkins.** The same test and build steps run on GitHub's own runners on every push. This gives a second, independent check that the project builds from a clean Linux checkout, without depending on the local Jenkins machine.

## Results

![Fig 1: Git history graph](figures/fig01.png)

Fig 1: Git history graph (`git log --graph --oneline --all --decorate`) showing feature branches and merge commits. It proves the branch and merge workflow.

![Fig 2: Jenkins job configuration](figures/fig02.png)

Fig 2: Jenkins job configuration (Pipeline script from SCM, repository URL, Poll SCM schedule). It proves the job reads the Jenkinsfile from the GitHub repository.

![Fig 3: The Jenkinsfile](figures/fig03.png)

Fig 3: The `Jenkinsfile` in the repository. It proves the pipeline is defined as code and shows the stages Checkout, Test, Build and Archive.

![Fig 4: Jenkins console output](figures/fig04.png)

Fig 4: Jenkins console output of a successful build, ending in `Finished: SUCCESS`. It proves the pipeline ran without errors, including the tests, packaging and archiving.

![Fig 5: Jenkins Stage View](figures/fig05.png)

Fig 5: Jenkins Stage View with the Test stage before the Build stage. It proves the tests run before the build.

![Fig 6: Archived artifact](figures/fig06.png)

Fig 6: Build page showing the archived artifact `habit-tracker.jar`. It proves the pipeline stores the JAR as its output.

![Fig 7: Build started by an SCM change](figures/fig07.png)

Fig 7: A build page showing "Started by an SCM change". It proves polling starts builds automatically after a new commit.

![Fig 8: GitHub Actions run](figures/fig08.png)

Fig 8: GitHub Actions run for the repository (CI workflow). It proves the test and build steps also run on a GitHub-hosted runner.

![Fig 9: GitHub Actions run steps and artifact](figures/fig09.png)

Fig 9: Details of a GitHub Actions run showing the Test and Build steps and the uploaded `habit-tracker` artifact. It proves the workflow runs the same steps as the Jenkins pipeline and uploads the JAR.

![Fig 10: Jenkins test result](figures/fig10.png)

Fig 10: Jenkins test result page for a build. It proves the Surefire results from `HabitControllerTest` are published to Jenkins.

## Conclusion

The component delivers a working CI pipeline. Code is versioned in Git with a branch, merge and pull request history, built with the Maven Wrapper, tested automatically, and packaged into an archived JAR by Jenkins. GitHub Actions runs the same test and build on every push alongside Jenkins.

Limitations: Jenkins runs on one local Windows machine and uses polling (`pollSCM`, every minute) instead of a webhook, so a build can start up to a minute after a push. The tests are controller-level tests only. Only successful builds are shown; no failing build was demonstrated. The pipeline stops at producing the JAR, and deployment (Ansible, Docker, Kubernetes) is done by hand in Component 2.

## References

1. Spring Boot Reference Documentation. https://docs.spring.io/spring-boot/index.html
2. Apache Maven Documentation. https://maven.apache.org/guides/index.html
3. Apache Maven Wrapper. https://maven.apache.org/wrapper/
4. Jenkins User Documentation: Pipeline. https://www.jenkins.io/doc/book/pipeline/
5. Jenkins Pipeline Syntax (Declarative Pipeline). https://www.jenkins.io/doc/book/pipeline/syntax/
6. GitHub Actions Documentation. https://docs.github.com/en/actions
7. Git Documentation. https://git-scm.com/doc