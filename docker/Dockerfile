# ==============================================================================
# Multi-Stage Dockerfile for Decoupled Java Microservices
# Stage 1: Build & Package (Optional if jars are built on host)
# Stage 2: Minimal Distroless / Temurin JRE 21 Runtime
# ==============================================================================

FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

ARG SERVICE_MODULE
ARG APP_JAR

# Copy the built jar from target
COPY ${SERVICE_MODULE}/target/${APP_JAR} app.jar

# Configure JVM flags: enable Virtual Threads optimization, container memory awareness
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080 8081 8082 8083

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
