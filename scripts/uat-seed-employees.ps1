param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Repo
$Project = "sgi-comando-uat"

if (-not $Force) {
    throw "Este script elimina y vuelve a cargar personal UAT. Ejecútelo explícitamente con -Force únicamente sobre una base UAT respaldada."
}

$deleteSql = Join-Path $Repo "database\uat-fixtures\DME_01_eliminar_todos_los_empleados_UAT.sql"
$seedSql   = Join-Path $Repo "database\uat-fixtures\DME_02_insertar_200_empleados_con_avatares_UAT.sql"

if (-not (Test-Path $deleteSql) -or -not (Test-Path $seedSql)) {
    throw "No se encontraron los fixtures UAT esperados."
}

Write-Host "ADVERTENCIA: operación destructiva UAT solicitada explícitamente." -ForegroundColor Red
Write-Host "Proyecto Docker: $Project" -ForegroundColor Yellow
Write-Host "Verificando contenedor PostgreSQL..." -ForegroundColor Cyan

$pgId = docker compose -p $Project ps -q postgres 2>$null | Select-Object -First 1
if (-not $pgId) { throw "PostgreSQL UAT no está levantado." }

Write-Host "Ejecutando DME_01 (reset controlado)..." -ForegroundColor Yellow
Get-Content -Raw $deleteSql | docker compose -p $Project exec -T postgres psql -v ON_ERROR_STOP=1 -U $env:POSTGRES_USER -d $env:POSTGRES_DB
if ($LASTEXITCODE -ne 0) { throw "DME_01 falló; no se ejecutará la carga." }

Write-Host "Ejecutando DME_02 (200 empleados UAT)..." -ForegroundColor Yellow
Get-Content -Raw $seedSql | docker compose -p $Project exec -T postgres psql -v ON_ERROR_STOP=1 -U $env:POSTGRES_USER -d $env:POSTGRES_DB
if ($LASTEXITCODE -ne 0) { throw "DME_02 falló." }

Write-Host "Fixture UAT aplicado explícitamente." -ForegroundColor Green
