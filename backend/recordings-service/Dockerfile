FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked chmod +x gradlew \
    && ./gradlew :backend:recordings-service:bootJar --no-daemon --max-workers=1

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/recordings-service/build/libs/recordings-service.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
