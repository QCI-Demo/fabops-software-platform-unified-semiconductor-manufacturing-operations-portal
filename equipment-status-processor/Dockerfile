# Multi-stage Dockerfile for FabOps Equipment Status Processor
# Build stage: Maven with OpenJDK 21
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Copy Maven wrapper and pom.xml first for layer caching
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw -B -DskipTests package && \
    cp target/equipment-status-processor-*.jar /workspace/app.jar

# Runtime stage: Minimal JRE with OpenJDK 21
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user for security
RUN groupadd --system fabops && \
    useradd --system --gid fabops fabops && \
    mkdir -p /tmp/kafka-streams && \
    chown -R fabops:fabops /tmp/kafka-streams

# Copy application from build stage
COPY --from=build /workspace/app.jar /app/app.jar

# Switch to non-root user
USER fabops

# Expose actuator port
EXPOSE 8080

# JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=default"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
