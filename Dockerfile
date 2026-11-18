# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Gradle wrapper (version in gradle/wrapper/gradle-wrapper.properties)
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon > /dev/null

# Copy source code and build (tests run in CI)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- -plain.jar)" app.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appuser && adduser -S appuser -G appuser

COPY --from=build /app/app.jar app.jar
RUN chown -R appuser:appuser /app

USER appuser:appuser

EXPOSE 8080

# Readiness includes the database
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider "http://localhost:8080/actuator/health/readiness" || exit 1

ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# The profile (SPRING_PROFILES_ACTIVE) comes from the environment/compose, not the image
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
