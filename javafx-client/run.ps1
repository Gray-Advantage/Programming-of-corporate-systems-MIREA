param([string]$BackendUrl = "http://localhost:8080")
$ErrorActionPreference = "Stop"
$taskRepo = Split-Path -Parent $PSScriptRoot
Push-Location $taskRepo
try {
    & .\mvnw.cmd -B -ntp -pl javafx-client -am install -DskipTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $env:BACKEND_URL = $BackendUrl
    & .\mvnw.cmd -pl javafx-client javafx:run
    exit $LASTEXITCODE
} finally { Pop-Location }
