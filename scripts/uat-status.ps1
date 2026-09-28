$ErrorActionPreference = "Continue"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Repo
$Project = "sgi-comando-uat"
Write-Host "=== SGI: Comando UAT containers ===" -ForegroundColor Cyan
docker compose -p $Project ps
Write-Host ""
Write-Host "=== Backend health ===" -ForegroundColor Cyan
try {
  $r = Invoke-WebRequest -Uri "http://localhost:8080/q/health/ready" -UseBasicParsing -TimeoutSec 3
  Write-Host "HTTP $($r.StatusCode) - Backend READY" -ForegroundColor Green
} catch {
  Write-Host "Backend health unavailable: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""
Write-Host "=== Frontend version ===" -ForegroundColor Cyan
try {
  $r = Invoke-WebRequest -Uri "http://localhost:5173/uat-version.json?ts=$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())" -UseBasicParsing -TimeoutSec 3 -Headers @{"Cache-Control"="no-cache"}
  Write-Host $r.Content -ForegroundColor Green
} catch {
  Write-Host "Frontend unavailable: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""
Write-Host "=== Last backend logs ===" -ForegroundColor Cyan
docker compose -p $Project logs backend --tail=180
