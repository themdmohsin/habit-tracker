# ---- Stage 1: build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build

# Copy only the wrapper and pom first so the dependency layer is cached
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
# Strip Windows CRLF line endings so the script runs on Linux
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B -q dependency:go-offline

# Source changes only invalidate layers from here down
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as a non-root user
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /build/target/habit-tracker.jar /app/app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
