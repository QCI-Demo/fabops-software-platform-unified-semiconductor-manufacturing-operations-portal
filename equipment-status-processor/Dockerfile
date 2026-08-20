# Multi-stage Dockerfile for Equipment Status Stream Processor
# Stage 1: Build with Maven
# Stage 2: Runtime with OpenJDK 21

# ============================================
# Stage 1: Build Stage
# ============================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B && \
    mv target/equipment-status-processor-*.jar target/app.jar

# ============================================
# Stage 2: Runtime Stage
# ============================================
FROM eclipse-temurin:21-jre-alpine

# Labels for container metadata
LABEL maintainer="FabOps Platform Team" \
      description="Kafka Streams processor for equipment health metrics and critical alerts" \
      version="1.0.0"

# Create non-root user for security
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

WORKDIR /app

# Create directories for Kafka Streams state store
RUN mkdir -p /tmp/kafka-streams && \
    chown -R appuser:appgroup /tmp/kafka-streams

# Copy the built artifact from builder stage
COPY --from=builder /build/target/app.jar app.jar

# Change ownership
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose actuator port for health checks
EXPOSE 8080

# JVM settings for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/heapdump.hprof \
               -Djava.security.egd=file:/dev/./urandom"

# Health check using actuator endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
