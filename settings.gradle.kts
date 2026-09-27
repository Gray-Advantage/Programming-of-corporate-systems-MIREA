rootProject.name = "teremok"

include(
    "backend:catalog-service",
    "backend:text-work-content-service",
    "backend:text-work-deployer",
    "backend:backend-shared:kafka-events",
    "console-client"
)
