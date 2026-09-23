# Stage 1: Build & Extract Layers
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /builder

# Copy Maven wrapper and pom.xml first for efficient dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build production package
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Extract Spring Boot layers using jarmode=tools with launcher support
RUN java -Djarmode=tools -jar target/*.jar extract --launcher --destination extracted/

# Stage 2: Minimal JVM Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy extracted application layers from builder
COPY --from=builder --chown=appuser:appgroup /builder/extracted/ ./

USER appuser
EXPOSE 8080

# Tuned for Render 512MB free tier RAM limits and rapid cold-start (SRS Section 26.7)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
