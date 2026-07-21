$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    New-Item -ItemType Directory -Force results | Out-Null
    docker compose up --build --abort-on-container-exit --exit-code-from java-client
    if ($LASTEXITCODE -ne 0) {
        throw "Spike-001 Compose execution failed with exit code $LASTEXITCODE"
    }
    docker compose cp java-client:/results/result.json results/result.json
    if ($LASTEXITCODE -ne 0) {
        throw "Spike-001 evidence export failed with exit code $LASTEXITCODE"
    }
} finally {
    docker compose down --volumes --remove-orphans
    Pop-Location
}
