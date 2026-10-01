param([string]$Test = "", [string]$Network = "sgi-comando_dev_default")
# Corre las pruebas del backend con JDK 25 dentro de Docker, contra la base sgi_comando_test y el MinIO del compose.
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
$vars = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; $vars[$k.Trim()] = $v.Trim() }
$exists = docker compose -f "$root/docker-compose.yml" exec -T postgres psql -U $vars.POSTGRES_USER -d postgres -tAc "select 1 from pg_database where datname='sgi_comando_test'"
if ($exists -ne "1") { docker compose -f "$root/docker-compose.yml" exec -T postgres psql -U $vars.POSTGRES_USER -d postgres -c "create database sgi_comando_test owner $($vars.POSTGRES_USER)" | Out-Null }
$mvn = @("-B", "test")
if ($Test) { $mvn += "-Dtest=$Test"; $mvn += "-Dsurefire.failIfNoSpecifiedTests=false" }
docker run --rm --network $Network `
  -v "${root}/backend:/workspace" -v sgi_comando_m2:/root/.m2 -w /workspace `
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/sgi_comando_test `
  -e QUARKUS_DATASOURCE_USERNAME=$($vars.POSTGRES_USER) -e QUARKUS_DATASOURCE_PASSWORD=$($vars.POSTGRES_PASSWORD) `
  -e SGI_MINIO_ENDPOINT=http://minio:9000 -e SGI_MINIO_ACCESS_KEY=$($vars.MINIO_ROOT_USER) -e SGI_MINIO_SECRET_KEY=$($vars.MINIO_ROOT_PASSWORD) `
  maven:3.9.11-eclipse-temurin-25 mvn @mvn
exit $LASTEXITCODE
