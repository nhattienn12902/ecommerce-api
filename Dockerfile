# =============================================================================
# Runtime-only Dockerfile — expects the JAR to be already built.
# Build the JAR first:  ./mvnw clean package -DskipTests
# Then build the image:  docker build -t ecommerce-api .
#
# The JAR is built on the CI runner (with test + Maven cache) rather than
# inside Docker, so the image build stays fast and does not re-run Maven.
# =============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Non-root user — created before USER switch so we still have root to create it.
RUN groupadd --system spring && useradd --system --gid spring spring

# Copy the pre-built fat JAR from the runner's target/ directory.
COPY target/*.jar app.jar
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# exec form so the JVM is PID 1 and receives SIGTERM for graceful shutdown.
ENTRYPOINT ["java", "-jar", "app.jar"]