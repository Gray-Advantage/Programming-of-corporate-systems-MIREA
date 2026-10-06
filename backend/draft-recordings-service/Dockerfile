FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked chmod +x gradlew \
    && ./gradlew :backend:draft-recordings-service:bootJar --no-daemon --max-workers=1

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/draft-recordings-service/build/libs/draft-recordings-service.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
