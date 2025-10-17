# Multi-stage build for IMS Connect Java Server
FROM openjdk:17-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy Maven files for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM openjdk:17-jre-slim

# Create non-root user
RUN groupadd -r imsconnect && useradd -r -g imsconnect imsconnect

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/ims-connect-java-server-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R imsconnect:imsconnect /app

# Switch to non-root user
USER imsconnect

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/v1/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]