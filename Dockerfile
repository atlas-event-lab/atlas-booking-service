# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────
# Self-contained multi-stage build. Tests are the CI gate (run via Gradle in the
# pipeline), so the image build only produces the bootJar.
#
# Two levels of layering, both deliberate:
#   1. Build cache — build descriptors are copied BEFORE sources, so a source-only
#      change reuses the cached dependency layer (CI: cache-from type=gha).
#   2. Runtime image — the bootJar is exploded into Spring Boot's four layers, so a
#      source-only change rewrites ~150 KB (application) instead of ~90 MB (fat jar).
#      Nodes re-pull only that layer. See DEPLOYMENT-RUNBOOK.md § 6.
#
# NOTE: `-Djarmode=tools` requires Spring Boot 3.3+. The older `-Djarmode=layertools`
# does NOT work here — Boot 3.5 ships spring-boot-jarmode-tools, not -layertools.
# ─────────────────────────────────────────────────────────────

# ─── Build stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 1) Wrapper layer — force the Gradle distribution download on its own, WITHOUT the
#    `|| true` below. A failure here (network, timeout, bad distributionUrl) must fail
#    the build loudly and immediately; swallowing it only defers the crash to the
#    bootJar step, where the real cause is no longer obvious.
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon --version

# 2) Dependency layer — cached until build.gradle / settings.gradle change. Tolerant on
#    purpose: this only warms the cache, and `dependencies` can fail on configurations
#    that are irrelevant to the bootJar. The wrapper is already known-good by now.
COPY settings.gradle build.gradle ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 3) Build layer — produce the bootJar, then explode it into layer directories.
#    `clean bootJar` runs the bootJar task only, so build/libs holds exactly one jar
#    (the `-plain.jar` comes from the `jar` task, which is not invoked here).
#    The mkdir guarantees all four directories exist: snapshot-dependencies is empty
#    on a release build, and COPY fails when its source directory is missing.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar \
 && cp build/libs/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted \
 && mkdir -p extracted/dependencies extracted/spring-boot-loader \
             extracted/snapshot-dependencies extracted/application

# ─── Runtime stage ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 1001 spring && adduser -S -u 1001 -G spring spring
WORKDIR /app

# Ordered least- to most-volatile. Ownership is set via COPY --chown and NEVER via a
# later `RUN chown`: OCI layers store whole files, not deltas, so chowning the jar
# afterwards would duplicate it in full inside the image.
COPY --from=builder --chown=1001:1001 /app/extracted/dependencies/ ./
COPY --from=builder --chown=1001:1001 /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=1001:1001 /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=1001:1001 /app/extracted/application/ ./

USER 1001:1001

ENV JAVA_OPTS="\
  -XX:MaxRAMPercentage=75 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9090/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
