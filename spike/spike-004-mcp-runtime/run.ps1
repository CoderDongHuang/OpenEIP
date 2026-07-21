$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    New-Item -ItemType Directory -Force results | Out-Null
    docker compose up --build --abort-on-container-exit --exit-code-from runner
    if ($LASTEXITCODE -ne 0) {
        throw "Spike-004 Compose execution failed with exit code $LASTEXITCODE"
    }
    docker compose cp runner:/results/result.json results/result.json
    if ($LASTEXITCODE -ne 0) {
        throw "Spike-004 evidence export failed with exit code $LASTEXITCODE"
    }
} finally {
    docker compose down --volumes --remove-orphans
    Pop-Location
}
