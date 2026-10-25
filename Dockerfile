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

# Extract to app.jar + lib/: the layout class data sharing (CDS) needs
RUN java -Djarmode=tools -jar target/*.jar extract --destination extracted/ --application-filename app.jar

# Stage 2: Minimal JVM Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy extracted application layers from builder
COPY --from=builder --chown=appuser:appgroup /builder/extracted/ ./

# CDS training run: start the app once at build time and record the classes it loads, so real starts read them
# from application.jsa instead of parsing ~10k classes on Render's 0.1 CPU. No database exists during the build,
# so Flyway and Hibernate's schema checks are off for this run only. The secret below is a throwaway, never used.
RUN java -XX:ArchiveClassesAtExit=application.jsa -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dsecurity.jwt.secret=00000000000000000000000000000000000000000000000000000000cd5cd5cd \
      -Dmail.provider=none -Dpush.enabled=false \
      -jar app.jar \
    && chown appuser:appgroup application.jsa

USER appuser
EXPOSE 8080

# Tuned for Render 512MB free tier RAM limits and rapid cold-start (SRS Section 26.7)
# TieredStopAtLevel=1: only the quick compiler. The optimising one competes for the single slow CPU while
# starting; measured at 0.1 CPU, startup fell from 466 s to 185 s. Peak speed barely matters at this traffic.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=application.jsa -jar app.jar"]
