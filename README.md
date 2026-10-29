# habit-tracker

Minimal Spring Boot 3 (Java 17, Maven) REST API with an in-memory list. Used as the sample app for a CI/CD pipeline assignment.

## Build

```
mvn clean package
```

Runs the tests and produces `target/habit-tracker.jar`.

## Run

```
java -jar target/habit-tracker.jar
```

Listens on port `8080` by default; override with the `PORT` env variable.

## Endpoints

- `GET /habits` - list habits
- `POST /habits` with `{"name": "Read"}` - create a habit (201); blank name returns 400
- `GET /actuator/health` - health check
