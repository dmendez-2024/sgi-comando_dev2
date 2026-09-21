$ErrorActionPreference = "Stop"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Repo
$Project = "sgi-comando-uat"

Write-Host "Stopping SGI: Comando UAT..." -ForegroundColor Cyan
# Suppress harmless Docker STDERR warnings so PowerShell 5.1 does not convert
# them into terminating NativeCommandError records.
cmd.exe /d /c "docker compose -p $Project down --remove-orphans >nul 2>&1" | Out-Null
Write-Host "SGI: Comando UAT stopped." -ForegroundColor Green
