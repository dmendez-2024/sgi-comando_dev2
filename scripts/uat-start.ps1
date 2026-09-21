$ErrorActionPreference = "Stop"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Repo

Write-Host "SGI: Comando - SER v0.8 UAT" -ForegroundColor Cyan
Write-Host "Stopping any previous UAT containers..."
docker compose down
if ($LASTEXITCODE -ne 0) { throw "docker compose down failed with exit code $LASTEXITCODE" }

Write-Host "Building and starting UAT..."
docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed with exit code $LASTEXITCODE" }

$ReadyUrl = "http://localhost:8080/q/health/ready"
$Ready = $false
Write-Host "Waiting for backend readiness..." -ForegroundColor Cyan
for ($i = 1; $i -le 60; $i++) {
    try {
        $response = Invoke-WebRequest -Uri $ReadyUrl -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            $Ready = $true
            break
        }
    } catch {
        # Backend may still be booting or Flyway may still be running.
    }
    Start-Sleep -Seconds 2
}

if (-not $Ready) {
    Write-Host "Backend did not become READY. Last backend logs:" -ForegroundColor Red
    docker compose logs backend --tail=150
    throw "UAT backend readiness check failed."
}

Write-Host "Backend READY." -ForegroundColor Green
Write-Host "Container status:" -ForegroundColor Cyan
docker compose ps
if ($LASTEXITCODE -ne 0) { throw "docker compose ps failed with exit code $LASTEXITCODE" }
