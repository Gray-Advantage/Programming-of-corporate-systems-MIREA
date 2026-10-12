FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked chmod +x mvnw \
    && ./mvnw -B -ntp -pl backend/text-work-deployer -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/text-work-deployer/target/text-work-deployer-jar-with-dependencies.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
