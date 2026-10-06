FROM golang:1.24.8-alpine AS builder
ARG MINIO_RELEASE=RELEASE.2025-10-15T17-29-55Z
RUN apk add --no-cache git
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go install github.com/minio/minio@${MINIO_RELEASE}

FROM alpine:3.22
RUN apk add --no-cache ca-certificates curl
COPY --from=builder /go/bin/minio /usr/local/bin/minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
