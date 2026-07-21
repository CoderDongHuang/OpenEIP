$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    New-Item -ItemType Directory -Force results | Out-Null
    Remove-Item -Force -ErrorAction SilentlyContinue results/consumer.done
    docker compose up --build --abort-on-container-exit --exit-code-from consumer
    if ($LASTEXITCODE -ne 0) {
        throw "Spike-002 Compose execution failed with exit code $LASTEXITCODE"
    }
    docker compose cp producer:/results/producer.json results/producer.json
    if ($LASTEXITCODE -ne 0) {
        throw "Spike-002 producer evidence export failed with exit code $LASTEXITCODE"
    }
    docker compose cp consumer:/results/result.json results/result.json
    if ($LASTEXITCODE -ne 0) {
        throw "Spike-002 consumer evidence export failed with exit code $LASTEXITCODE"
    }
} finally {
    docker compose down --volumes --remove-orphans
    Pop-Location
}
