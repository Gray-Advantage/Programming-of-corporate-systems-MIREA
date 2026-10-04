FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked chmod +x gradlew \
    && ./gradlew :backend:database-exporter:installDist --no-daemon --max-workers=1

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backend/database-exporter/build/install/database-exporter ./
ENTRYPOINT ["./bin/database-exporter"]
