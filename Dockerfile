# Stage 1: Copy Avro schemas from avro-schemas image
FROM ghcr.io/rensights/avro-schemas:latest AS schemas

# Stage 2: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy Avro schemas from schemas stage
COPY --from=schemas /schemas/schemas ./schemas/avro

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build the application (this will generate Avro classes from schemas)
WORKDIR /app/src
RUN mvn clean package -DskipTests

# Stage 3: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Download OpenTelemetry Java agent
RUN wget -O opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Copy built JAR from builder
COPY --from=builder /app/src/target/*.jar app.jar

EXPOSE 8080

# Run with OpenTelemetry Java agent for auto-instrumentation
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
