# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy Maven files first for better layer caching
COPY pom.xml settings.xml* ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q -DskipTests

# Copy source code
COPY src ./src

# Build the application
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -q -DskipTests && \
    mv target/*.jar app.jar

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl for health checks, then remove unnecessary packages
RUN apk add --no-cache curl && \
    apk del --purge apk-tools && \
    rm -rf /var/cache/apk/* /tmp/* /var/tmp/*

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create logs and plugins directories with proper permissions
RUN mkdir -p /app/logs /app/plugins && chown -R appuser:appgroup /app/logs /app/plugins

# Copy JAR from builder
COPY --from=builder --chown=appuser:appgroup /build/app.jar .

# Run as non-root user
USER appuser

# Health check using curl (works reliably in Alpine)
HEALTHCHECK --interval=10s --timeout=5s --retries=20 --start-period=30s \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
