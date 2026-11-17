# ==============================================================================
# Multi-Stage Layered Dockerfile for Decoupled Java Microservices
# Stage 1: Extract Spring Boot layered layout (eliminates nested ZIP overhead)
# Stage 2: Minimal Alpine Temurin JRE 21 Runtime with Cached Dependency Layers
# ==============================================================================

FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /extract

ARG SERVICE_MODULE
ARG APP_JAR

COPY ${SERVICE_MODULE}/target/${APP_JAR} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --application-filename application.jar --destination extracted \
    && mkdir -p extracted/dependencies extracted/spring-boot-loader extracted/snapshot-dependencies extracted/application

FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy layers from least-frequently changed (dependencies) to most-frequently changed (application)
COPY --from=extractor --chown=appuser:appgroup /extract/extracted/dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /extract/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=appuser:appgroup /extract/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /extract/extracted/application/ ./

# Fast local dev startup: skip C2 compilation (-XX:TieredStopAtLevel=1) + Class Data Sharing (-Xshare:auto)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:TieredStopAtLevel=1 -Xshare:auto -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080 8081 8082 8083 8084 8085 8090

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
