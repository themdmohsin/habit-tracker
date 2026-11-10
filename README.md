# habit-tracker

Small Spring Boot 3 (Java 17, Maven) habit tracker with a REST API and a one-page UI. Data is kept in memory only (no database). Used as the sample app for a CI/CD pipeline assignment.

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

- `GET /habits` - list habits, each with `id, name, category, createdAt, doneToday, streak, totalCheckIns`
- `POST /habits` with `{"name": "Read", "category": "Learning"}` - create a habit (201); `category` is optional (default `General`); blank name returns 400
- `POST /habits/{id}/check-in` - mark today as done (idempotent per day), returns the habit; 404 if unknown
- `DELETE /habits/{id}` - delete (204); 404 if unknown
- `GET /info` - `{"pod": "<hostname>", "version": "3.0"}`
- `GET /actuator/health` - health check

## UI

Open `http://localhost:8080/` (single file `src/main/resources/static/index.html`, vanilla JS, no build step). Add habits, tick "Done today", delete, and see the serving pod in the footer.

Jenkins automatic CI trigger verification.
