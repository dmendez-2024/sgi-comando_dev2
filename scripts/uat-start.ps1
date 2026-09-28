$ErrorActionPreference = "Stop"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Repo

$Project = "sgi-comando-uat"

$EnvFile = Join-Path $Repo ".env"
if (-not (Test-Path $EnvFile)) {
    throw "Falta .env. Copie .env.example a .env, reemplace todos los CHANGE_ME y no lo agregue a Git."
}
$UnresolvedEnvLines = Get-Content $EnvFile | Where-Object {
    $line = $_.Trim()
    $line -and -not $line.StartsWith("#") -and $line -match "=CHANGE_ME(?:$|\s)"
}
if ($UnresolvedEnvLines) {
    throw ".env contains unresolved placeholder values. Configure UAT secrets before startup."
}
$ExpectedFrontendVersion = "0.1.0"

Write-Host "SGI: Comando - NEX v0.1 FROZEN UAT / Entrega SISTEMAS 2026-09-28 / SITC v4.1 / CSL v0.2.5 baseline" -ForegroundColor Cyan

# IMPORTANT: Docker sometimes writes harmless warnings to STDERR (for example
# "No resource found to remove"). With PowerShell 5.1 + ErrorActionPreference=Stop
# that warning can become a terminating NativeCommandError. Cleanup therefore
# runs through cmd.exe with output suppressed and is intentionally best-effort.
Write-Host "Cleaning previous SGI: Comando UAT containers..." -ForegroundColor Cyan
cmd.exe /d /c "docker compose -p $Project down --remove-orphans >nul 2>&1" | Out-Null

# Older SGI: Comando UAT releases used the generic Compose project name 'repo'.
# Remove only the four known SGI containers by exact name; do NOT run
# `docker compose -p repo down`, because other UAT projects may also use 'repo'.
$LegacySgiContainers = @(
    "repo-backend-1",
    "repo-frontend-1",
    "repo-postgres-1",
    "repo-minio-1"
)
foreach ($containerName in $LegacySgiContainers) {
    cmd.exe /d /c "docker rm -f $containerName >nul 2>&1" | Out-Null
}

Write-Host "Building and starting UAT..." -ForegroundColor Cyan
docker compose -p $Project up --build --force-recreate -d
$ComposeUpExitCode = $LASTEXITCODE
if ($ComposeUpExitCode -ne 0) {
    Write-Host "docker compose up failed. Current status:" -ForegroundColor Red
    docker compose -p $Project ps
    Write-Host "Frontend build/log tail:" -ForegroundColor Yellow
    docker compose -p $Project logs frontend --tail=120 2>$null
    throw "docker compose up failed with exit code $ComposeUpExitCode"
}

$ReadyUrl = "http://localhost:8080/q/health/ready"
$Ready = $false
Write-Host "Waiting for backend readiness..." -ForegroundColor Cyan

for ($i = 1; $i -le 60; $i++) {
    $backendId = (docker compose -p $Project ps -q backend 2>$null | Select-Object -First 1)
    if ($backendId) {
        $status = (docker inspect -f '{{.State.Status}}' $backendId 2>$null)
        if ($LASTEXITCODE -eq 0 -and ($status -eq 'exited' -or $status -eq 'dead' -or $status -eq 'restarting')) {
            Write-Host "Backend container status: $status" -ForegroundColor Red
            Write-Host "Backend logs:" -ForegroundColor Yellow
            docker compose -p $Project logs backend --tail=180
            throw "Backend container failed during startup."
        }
    }

    try {
        $response = Invoke-WebRequest -Uri $ReadyUrl -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            $Ready = $true
            break
        }
    } catch {
        # Backend is still starting.
    }

    if (($i % 5) -eq 0) {
        $recentBackendLogs = (docker compose -p $Project logs backend --tail=80 2>&1 | Out-String)
        if ($recentBackendLogs -match 'Failed to start application|FlywayValidateException|Migration checksum mismatch|ERROR.*Failed to start quarkus') {
            Write-Host "Backend reported a fatal startup error:" -ForegroundColor Red
            Write-Host $recentBackendLogs
            throw "Backend failed during startup."
        }
        Write-Host "  ... $i seconds" -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds 1
}

if (-not $Ready) {
    Write-Host "Backend did not become READY within 60 seconds." -ForegroundColor Red
    Write-Host "Container status:" -ForegroundColor Yellow
    docker compose -p $Project ps
    Write-Host "Backend logs:" -ForegroundColor Yellow
    docker compose -p $Project logs backend --tail=180
    throw "UAT backend readiness check failed. Diagnostic output is shown above."
}

Write-Host "Backend READY." -ForegroundColor Green

$FrontendReady = $false
Write-Host "Checking frontend version..." -ForegroundColor Cyan
for ($i = 1; $i -le 20; $i++) {
    try {
        $url = "http://localhost:5173/uat-version.json?ts=$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
        $frontendResponse = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 -Headers @{"Cache-Control"="no-cache"}
        if ($frontendResponse.StatusCode -eq 200 -and $frontendResponse.Content -match $ExpectedFrontendVersion) {
            $FrontendReady = $true
            break
        }
    } catch {
        # Frontend is still starting.
    }
    Start-Sleep -Seconds 1
}

if (-not $FrontendReady) {
    Write-Host "Frontend version mismatch or frontend not ready. Rebuilding frontend without Docker cache..." -ForegroundColor Yellow
    docker compose -p $Project build --no-cache frontend
    if ($LASTEXITCODE -ne 0) { throw "frontend no-cache build failed with exit code $LASTEXITCODE" }
    docker compose -p $Project up -d --force-recreate frontend
    if ($LASTEXITCODE -ne 0) { throw "frontend restart failed with exit code $LASTEXITCODE" }
    Start-Sleep -Seconds 3

    $url = "http://localhost:5173/uat-version.json?ts=$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
    try {
        $frontendResponse = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -Headers @{"Cache-Control"="no-cache"}
    } catch {
        docker compose -p $Project logs frontend --tail=120
        throw "UAT frontend did not respond after rebuild."
    }
    if ($frontendResponse.StatusCode -ne 200 -or $frontendResponse.Content -notmatch $ExpectedFrontendVersion) {
        docker compose -p $Project logs frontend --tail=120
        throw "UAT frontend version check failed: expected NEX v$ExpectedFrontendVersion"
    }
}

Write-Host "Frontend NEX v$ExpectedFrontendVersion READY." -ForegroundColor Green
Write-Host "Container status:" -ForegroundColor Cyan
docker compose -p $Project ps
if ($LASTEXITCODE -ne 0) { throw "docker compose ps failed with exit code $LASTEXITCODE" }
Write-Host "UAT READY. Run .\repo\scripts\uat-open.ps1 to open SGI: Comando." -ForegroundColor Green
