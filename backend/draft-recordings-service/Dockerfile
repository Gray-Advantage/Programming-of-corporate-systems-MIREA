FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked chmod +x mvnw \
    && ./mvnw -B -ntp -pl backend/draft-recordings-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/draft-recordings-service/target/draft-recordings-service.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
