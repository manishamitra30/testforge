# syntax=docker/dockerfile:1
#
# The build context is the REPOSITORY ROOT, because the image also carries the automation suite:
#   docker build -f backend/Dockerfile -t testforge-backend .
#
# Build arg WITH_AUTOMATION (default true):
#   true  -> JDK + Chromium + chromedriver, so "automated" runs work inside the container (big image)
#   false -> plain JRE only, small image for hosts with little memory (automated runs are unavailable)

# ---- 1. build the jar ----------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && sh ./mvnw -B -ntp dependency:go-offline
COPY backend/src src
RUN sh ./mvnw -B -ntp -DskipTests package \
 && cp target/backend-*.jar app.jar

# ---- 2. runtime ----------------------------------------------------------------------
FROM debian:bookworm-slim AS runtime
ARG WITH_AUTOMATION=true

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && if [ "$WITH_AUTOMATION" = "true" ]; then \
        apt-get install -y --no-install-recommends openjdk-17-jdk-headless chromium chromium-driver fonts-liberation; \
    else \
        apt-get install -y --no-install-recommends openjdk-17-jre-headless; \
    fi \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --uid 10001 app
WORKDIR /app
COPY --from=build /build/app.jar app.jar

# The Selenium/Cucumber suite that "automated" runs execute. Copied in both variants (it is small);
# the Maven dependencies are pre-downloaded only when the browser tooling is installed.
COPY --chown=app:app automation-suite /opt/automation-suite
RUN if [ "$WITH_AUTOMATION" = "true" ]; then \
        sed -i 's/\r$//' /opt/automation-suite/mvnw \
     && runuser -u app -- sh -c 'cd /opt/automation-suite && sh ./mvnw -B -ntp dependency:go-offline' \
     || echo "Maven warm-up skipped; dependencies will download on the first automated run"; \
    fi

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC" \
    APP_AUTOMATION_SUITE_DIR=/opt/automation-suite \
    TESTFORGE_NO_SANDBOX=true \
    CHROME_BIN=/usr/bin/chromium \
    CHROMEDRIVER_PATH=/usr/bin/chromedriver

USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://localhost:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
