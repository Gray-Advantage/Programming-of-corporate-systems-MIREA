FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew \
    && ./gradlew :backend:text-work-content-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/text-work-content-service/build/libs/text-work-content-service.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
