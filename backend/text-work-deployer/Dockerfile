FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew \
    && ./gradlew :backend:text-work-deployer:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/text-work-deployer/build/install/text-work-deployer ./
ENTRYPOINT ["/app/bin/text-work-deployer"]
