# syntax=docker/dockerfile:1

############################################
# Stage 1: build the Spring Boot jar
############################################
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first, so this layer is cached until they change.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./

# Windows checkouts can leave CRLF line endings, which break the script on Linux.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src src

# The cache mount keeps downloaded Gradle dependencies between builds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test

############################################
# Stage 2: small runtime image (JRE only)
############################################
FROM eclipse-temurin:21-jre AS runtime

# curl is used by the compose health check.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
RUN mkdir -p /app/logs && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/build/libs/app.jar /app/app.jar

USER app

# No secrets here. Credentials arrive as environment variables at runtime.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Kolkata"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]