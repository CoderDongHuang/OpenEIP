$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$spikes = @(
    "spike-001-grpc-java-python",
    "spike-002-kafka-eventing",
    "spike-003-milvus-vector",
    "spike-004-mcp-runtime",
    "spike-005-llm-streaming"
)

foreach ($spike in $spikes) {
    Write-Host "Running $spike"
    & (Join-Path $root "$spike/run.ps1")
}

Write-Host "All Phase 1.5 Spikes passed."
