# ─────────────────────────────────────────────
# 1. Build stage
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY . .

RUN ./gradlew clean bootJar --no-daemon


# ─────────────────────────────────────────────
# 2. Runtime stage
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown spring:spring app.jar

USER spring:spring

# JVM optimized for containers
ENV JAVA_OPTS="\
  -XX:MaxRAMPercentage=75 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9090/actuator/health/readiness || exit 1

# Entrypoint with JVM opts
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]