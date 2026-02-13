FROM gradle:8.10-jdk21 AS builder
WORKDIR /workspace
COPY . .
RUN gradle clean build -x test


FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
# 메모리 절약을 위해 HEALTHCHECK 주석 처리, 추후 업그레이드시 주석 제거
# HEALTHCHECK --interval=30s --timeout=3s --retries=10 CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# docker-compose.yml 의 environment 값을 따름
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]