# =============================================================================
# STAGE 1 — BUILDER: compile source → JAR
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# --- Dependency layer: tách pom.xml ra trước để tận dụng cache ---
# pom.xml đổi hiếm, source đổi thường xuyên. Copy pom trước + go-offline
# nghĩa là layer tải dependency chỉ chạy lại khi pom.xml thay đổi,
# không phải mỗi lần sửa code.
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN mvn dependency:go-offline -B

# --- Build layer: copy source rồi package ---
# Layer này invalidate mỗi khi src đổi — nhưng dependency ở trên vẫn được cache.
COPY src ./src
RUN mvn clean package -DskipTests -B

# =============================================================================
# STAGE 2 — RUNTIME: chỉ JRE + JAR
# =============================================================================
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# --- Non-root user: tạo TRƯỚC khi switch, để còn quyền root mà tạo ---
# Least privilege: nếu app bị compromise, attacker không có root trong container.
RUN groupadd --system spring && useradd --system --gid spring spring

# --- Copy JAR từ builder stage ---
# Đây là điểm cốt lõi của multi-stage: chỉ JAR đi vào image cuối,
# mọi thứ khác (Maven, JDK, source, .m2) bị bỏ lại ở builder stage.
COPY --from=builder /app/target/*.jar app.jar

# --- Chuyển ownership JAR sang non-root user ---
# Phải chown TRƯỚC khi USER spring, vì sau khi switch không còn quyền root.
RUN chown spring:spring app.jar

# --- Switch sang non-root ---
USER spring

# --- Expose port (documentation, không thực sự publish) ---
# EXPOSE chỉ mang tính khai báo. Publish thật diễn ra lúc docker run -p.
EXPOSE 8080

# --- Entrypoint ---
# exec form (JSON array) để JVM nhận signal đúng cách (SIGTERM khi stop).
# Nếu dùng shell form ("java -jar app.jar"), JVM chạy dưới shell,
# không nhận được SIGTERM → graceful shutdown không hoạt động.
ENTRYPOINT ["java", "-jar", "app.jar"]