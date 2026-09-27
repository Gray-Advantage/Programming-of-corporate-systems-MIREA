FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew \
    && ./gradlew :backend:catalog-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/catalog-service/build/libs/catalog-service.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
