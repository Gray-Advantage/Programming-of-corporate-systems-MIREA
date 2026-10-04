rootProject.name = "teremok"

include(
    "backend:catalog-service",
    "backend:text-work-content-service",
    "backend:text-work-deployer",
    "backend:database-exporter",
    "backend:backend-shared:kafka-events",
    "backend-client-shared",
    "console-client"
)
