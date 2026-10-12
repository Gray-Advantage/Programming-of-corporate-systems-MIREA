FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked chmod +x mvnw \
    && ./mvnw -B -ntp -pl backend/auth-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/auth-service/target/auth-service.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
