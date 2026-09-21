$ErrorActionPreference = "Stop"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Repo

Write-Host "WARNING: local UAT volumes will be deleted." -ForegroundColor Yellow
docker compose down -v
if ($LASTEXITCODE -ne 0) { throw "docker compose down -v failed with exit code $LASTEXITCODE" }

docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed with exit code $LASTEXITCODE" }

docker compose ps
if ($LASTEXITCODE -ne 0) { throw "docker compose ps failed with exit code $LASTEXITCODE" }
