# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────
# Self-contained multi-stage build. Tests are the CI gate (run via Gradle in the
# pipeline), so the image build only produces the bootJar.
# Layer order is optimized: build descriptors are copied BEFORE sources, so a
# source-only change reuses the cached dependency layer (CI: cache-from type=gha).
# ─────────────────────────────────────────────────────────────

# ─── Build stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 1) Dependency layer — cached until build.gradle / settings.gradle change.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 2) Build layer — copy sources and produce the bootJar.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ─── Runtime stage ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 1001 spring && adduser -S -u 1001 -G spring spring
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown spring:spring app.jar
USER 1001:1001

ENV JAVA_OPTS="\
  -XX:MaxRAMPercentage=75 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9090/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
