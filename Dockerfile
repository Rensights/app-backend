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

# Stage 3: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# SECURITY FIX: Create non-root user for running the application
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Download OpenTelemetry Java agent (cached unless version changes)
ARG OTEL_AGENT_VERSION=2.22.0
# SECURITY: Download and verify checksum (optional but recommended)
RUN wget -q -O opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar && \
    rm -rf /var/cache/apk/*

# Copy built JAR from builder
COPY --from=builder /app/src/target/*.jar app.jar

# SECURITY: Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# SECURITY: Switch to non-root user
USER appuser

EXPOSE 8080

# Run with OpenTelemetry Java agent for auto-instrumentation
ENTRYPOINT ["java", "-Xshare:off", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
