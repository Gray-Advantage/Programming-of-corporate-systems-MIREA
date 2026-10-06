rootProject.name = "teremok"

include(
    "backend:auth-service",
    "backend:catalog-service",
    "backend:draft-recordings-service",
    "backend:recordings-service",
    "backend:renderer-service",
    "backend:text-work-content-service",
    "backend:text-work-deployer",
    "backend:database-exporter",
    "backend:backend-shared:kafka-events",
    "backend:backend-shared:jwt-auth",
    "backend:backend-shared:object-storage",
    "backend-client-shared",
    "console-client"
)
