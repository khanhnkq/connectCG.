# --- Giai đoạn 1: Build ứng dụng ---
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# 1. Copy các file cấu hình Gradle trước
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Chuyển về Gradle 8.7 bên trong Docker và cấp quyền
RUN sed -i 's/gradle-9.2.1-bin.zip/gradle-8.7-bin.zip/g' gradle/wrapper/gradle-wrapper.properties && chmod +x gradlew

# 2. Tải trước Gradle wrapper và toàn bộ thư viện dependencies vào cache của Docker
# (Layer này sẽ được cache vĩnh viễn, chỉ tải 1 lần duy nhất)
RUN ./gradlew dependencies --no-daemon || true

# 3. Copy toàn bộ source code (chỉ copy sau khi dependencies đã được cache)
COPY src src

# 4. Thực hiện compile và đóng gói JAR siêu tốc từ cache
RUN ./gradlew bootJar -x test --no-daemon

# --- Giai đoạn 2: Chạy ứng dụng ---
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Chỉ copy file .jar từ giai đoạn builder sang
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Cấu hình JVM tối ưu
ENTRYPOINT ["java", "-Xms128m", "-Xmx300m", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-jar", "app.jar"]