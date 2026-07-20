[CmdletBinding()]
param(
    [ValidateSet("All", "Vulnerability", "Misconfiguration", "Secret")]
    [string]$Scan = "All",

    [string]$TrivyImage = "aquasec/trivy:0.72.0"
)

$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$repoPrefix = $repoRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$tempPrefix = $tempRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$snapshot = [System.IO.Path]::Combine($tempRoot, "openeip-trivy-" + [System.Guid]::NewGuid().ToString("N"))
$cacheVolume = "openeip-trivy-cache"
$scanFailed = $false

function Invoke-TrivyScan {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Scanners
    )

    Write-Host "Running Trivy scanners: $Scanners"
    & docker run --rm `
        --mount "type=bind,source=$snapshot,target=/workspace,readonly" `
        --mount "type=volume,source=$cacheVolume,target=/root/.cache/trivy" `
        $TrivyImage fs `
        --scanners $Scanners `
        --severity HIGH,CRITICAL `
        --ignore-unfixed `
        --exit-code 1 `
        --no-progress `
        --skip-version-check `
        /workspace

    if ($LASTEXITCODE -ne 0) {
        $script:scanFailed = $true
    }
}

try {
    & git -C $repoRoot rev-parse --is-inside-work-tree *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "The repository root is not a Git work tree: $repoRoot"
    }

    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not available. Start Docker Desktop and retry."
    }

    New-Item -ItemType Directory -Path $snapshot | Out-Null
    $files = @(& git -C $repoRoot ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to enumerate Git-relevant files."
    }

    foreach ($relativePath in $files) {
        $source = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $relativePath))
        if (-not $source.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to copy a path outside the repository: $relativePath"
        }

        $destination = Join-Path $snapshot $relativePath
        $destinationDirectory = Split-Path -Parent $destination
        if (-not (Test-Path -LiteralPath $destinationDirectory)) {
            New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
        }
        Copy-Item -LiteralPath $source -Destination $destination
    }

    $size = (Get-ChildItem -LiteralPath $snapshot -File -Recurse | Measure-Object -Property Length -Sum).Sum
    Write-Host ("Prepared {0} files ({1:N2} MB) in a clean scan snapshot." -f $files.Count, ($size / 1MB))

    switch ($Scan) {
        "Vulnerability" { Invoke-TrivyScan -Scanners "vuln" }
        "Misconfiguration" { Invoke-TrivyScan -Scanners "misconfig" }
        "Secret" { Invoke-TrivyScan -Scanners "secret" }
        "All" {
            Invoke-TrivyScan -Scanners "vuln,misconfig"
            Invoke-TrivyScan -Scanners "secret"
        }
    }
}
finally {
    $resolvedSnapshot = [System.IO.Path]::GetFullPath($snapshot)
    if ($resolvedSnapshot.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $resolvedSnapshot).StartsWith("openeip-trivy-", [System.StringComparison]::Ordinal)) {
        Remove-Item -LiteralPath $resolvedSnapshot -Recurse -Force -ErrorAction SilentlyContinue
    }
    else {
        Write-Warning "Skipped cleanup because the snapshot path was not under the system temp directory: $resolvedSnapshot"
    }
}

if ($scanFailed) {
    Write-Error "One or more Trivy scans reported findings or failed."
    exit 1
}

Write-Host "Trivy scan completed successfully."
