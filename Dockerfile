# Build: the Spring Boot jar (tests run in CI/locally, not here).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- -plain.jar)" app.jar

# Run: profile prod, configured only through environment variables (see deploy/.env.example).
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 educare
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER educare
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
