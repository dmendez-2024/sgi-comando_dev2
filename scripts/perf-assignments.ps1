param(
    [Parameter(Mandatory=$true)][string]$CompanyId,
    [Parameter(Mandatory=$true)][string]$WeekStart,
    [string]$EmployeeId = "",
    [int]$Pages = 3,
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
Write-Host "SGI: Comando - Assignment performance smoke test" -ForegroundColor Cyan
Write-Host "Company: $CompanyId  Week: $WeekStart" -ForegroundColor DarkGray

for ($page = 0; $page -lt $Pages; $page++) {
    $url = "$BaseUrl/api/assignments/personnel?companyId=$CompanyId&weekStart=$WeekStart&page=$page&size=100"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
    $sw.Stop()
    $bytes = [Text.Encoding]::UTF8.GetByteCount($response.Content)
    $json = $response.Content | ConvertFrom-Json
    Write-Host ("Personnel page {0}: {1} ms | {2} rows | {3:N0} bytes | total={4}" -f $page,$sw.ElapsedMilliseconds,$json.items.Count,$bytes,$json.total)
}

if ($EmployeeId) {
    $planUrl = "$BaseUrl/api/assignments/week?companyId=$CompanyId&weekStart=$WeekStart"
    $week = Invoke-RestMethod -Uri $planUrl -Method Get -TimeoutSec 30
    $evalUrl = "$BaseUrl/api/assignments/evaluate?planId=$($week.plan.id)&employeeId=$EmployeeId"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $eval = Invoke-RestMethod -Uri $evalUrl -Method Get -TimeoutSec 30
    $sw.Stop()
    Write-Host ("Evaluation: {0} ms | {1} shift evaluations" -f $sw.ElapsedMilliseconds,$eval.Count)
}
