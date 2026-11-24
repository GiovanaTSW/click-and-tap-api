# syntax=docker/dockerfile:1

# ---------- Stage 1: build the jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY src src

# The cache mount keeps downloaded dependencies between builds (it is not part of the image).
# Tests are not run here: CI runs them before this image is built.
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon bootJar -x test

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run the application as root.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/build/libs/app.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=pdn \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
