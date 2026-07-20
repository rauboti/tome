# syntax=docker/dockerfile:1

# ---- Build stage: compile + package the Spring Boot fat jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Wrapper + manifest first; sources after. Tests are SKIPPED here on purpose:
# they use Testcontainers, which needs a Docker daemon not available inside
# `docker build`. CI stays the test gate (`mvnw verify`).
# The m2 cache mount speeds up repeat local builds.
COPY .mvn ./.mvn
COPY mvnw pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked sh ./mvnw -B clean package -DskipTests

# ---- Runtime stage: slim JRE + the jar, non-root ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
