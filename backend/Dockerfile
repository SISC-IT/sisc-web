FROM gradle:8.10-jdk21 AS builder
WORKDIR /workspace
COPY . .
RUN gradle clean build -x test


FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
# ENTRYPOINT ["java", "-Xmx400m", "-jar", "/app/app.jar"] # 메모리 부족 시 아래 코드와 교환
ENTRYPOINT ["java","-jar","/app/app.jar"]