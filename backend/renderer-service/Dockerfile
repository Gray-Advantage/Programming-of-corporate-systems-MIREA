FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked chmod +x gradlew \
    && ./gradlew :backend:renderer-service:bootJar --no-daemon --max-workers=1

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /app
COPY --from=builder /workspace/backend/renderer-service/build/libs/renderer-service.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
