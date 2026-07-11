# Stage 1: Copy Avro schemas from avro-schemas image
# Use build arg to allow version pinning (default: latest)
ARG AVRO_SCHEMAS_VERSION=latest
FROM ghcr.io/rensights/avro-schemas:${AVRO_SCHEMAS_VERSION} AS schemas

# Stage 2: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy Avro schemas from schemas stage (cached layer)
COPY --from=schemas /schemas/schemas ./schemas/avro

# Copy Maven configuration files first (better layer caching)
# Only copy pom.xml files first to leverage Maven dependency cache
COPY pom.xml .
COPY src/pom.xml ./src/

# Download dependencies (this layer will be cached unless pom.xml changes)
WORKDIR /app/src
RUN mvn dependency:go-offline -B || true

# Now copy source code (this layer only invalidates when code changes)
WORKDIR /app
COPY src ./src

# Build the application (this will generate Avro classes from schemas)
WORKDIR /app/src
RUN mvn clean package -DskipTests -B

# Build a no-op OTel agent stub JAR so the JVM can load -javaagent without crashing
# (some cluster-level tooling injects -javaagent:/app/opentelemetry-javaagent.jar unconditionally)
RUN mkdir -p /tmp/noop/src/noop /tmp/noop/out && \
    printf 'package noop;\nimport java.lang.instrument.Instrumentation;\npublic class Agent { public static void premain(String a, Instrumentation i) {} }\n' \
      > /tmp/noop/src/noop/Agent.java && \
    javac /tmp/noop/src/noop/Agent.java -d /tmp/noop/out && \
    printf 'Manifest-Version: 1.0\nPremain-Class: noop.Agent\n\n' > /tmp/noop/MANIFEST.MF && \
    jar cfm /tmp/noop-agent.jar /tmp/noop/MANIFEST.MF -C /tmp/noop/out .

# Stage 3: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# SECURITY FIX: Create non-root user for running the application
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy built JAR from builder
COPY --from=builder /app/src/target/*.jar app.jar

# Place the no-op stub at the exact path the cluster injects as -javaagent
COPY --from=builder /tmp/noop-agent.jar /app/opentelemetry-javaagent.jar

# Grafana OpenTelemetry Java agent (application observability -> Grafana)
ADD https://github.com/grafana/grafana-opentelemetry-java/releases/latest/download/grafana-opentelemetry-java.jar /app/

# SECURITY: Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# SECURITY: Switch to non-root user
USER appuser

EXPOSE 8080

# Load the Grafana OpenTelemetry Java agent via JAVA_TOOL_OPTIONS.
# (_JAVA_OPTIONS / JDK_JAVA_OPTIONS stay cleared so any cluster-level injection into
# those channels is still neutralized.)
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/grafana-opentelemetry-java.jar" \
    _JAVA_OPTIONS="" \
    JDK_JAVA_OPTIONS=""
ENV GRAFANA_OTEL_APPLICATION_OBSERVABILITY_METRICS=true
ENV OTEL_SERVICE_NAME=app-backend

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
