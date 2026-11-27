<#
  Smoke test for a running TestForge deployment.

  .\scripts\smoke-test.ps1 -ApiUrl https://testforge-backend.onrender.com -FrontendUrl https://testforge.vercel.app
  .\scripts\smoke-test.ps1 -ApiUrl http://localhost:8080 -FrontendUrl http://localhost:3000

  It registers a throw-away user, walks the main flows, deletes the project it created and exits
  with code 1 when anything fails. The first call retries for up to ~100 s so a sleeping free-tier
  backend has time to wake up.
#>
param(
    [Parameter(Mandatory = $true)] [string] $ApiUrl,
    [Parameter(Mandatory = $true)] [string] $FrontendUrl
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$api = $ApiUrl.TrimEnd("/")
$web = $FrontendUrl.TrimEnd("/")
$results = New-Object System.Collections.ArrayList

function Check([string] $name, [scriptblock] $body) {
    try {
        $detail = & $body
        [void]$results.Add([pscustomobject]@{ Name = $name; Ok = $true; Detail = "$detail" })
        Write-Host ("  PASS  {0}  {1}" -f $name, $detail) -ForegroundColor Green
    } catch {
        [void]$results.Add([pscustomobject]@{ Name = $name; Ok = $false; Detail = $_.Exception.Message })
        Write-Host ("  FAIL  {0}  {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
    }
}

function StatusOf([scriptblock] $request) {
    try { $r = & $request; return [int]$r.StatusCode }
    catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

function Api([string] $method, [string] $path, $body = $null, [string] $token = $null) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $req = @{ Method = $method; Uri = "$api$path"; Headers = $headers; ContentType = "application/json"; UseBasicParsing = $true }
    if ($null -ne $body) { $req["Body"] = ($body | ConvertTo-Json -Depth 8) }
    return Invoke-RestMethod @req
}

Write-Host ""
Write-Host "TestForge smoke test" -ForegroundColor Cyan
Write-Host "  API      $api"
Write-Host "  Frontend $web"
Write-Host ""

$token = $null
$projectId = $null
$username = "smoke" + (Get-Random -Minimum 10000 -Maximum 99999)
$password = "Secret123!"

Check "Backend health (waits for a cold start)" {
    $health = $null
    for ($i = 1; $i -le 12; $i++) {
        try { $health = Invoke-RestMethod -Uri "$api/actuator/health" -UseBasicParsing -TimeoutSec 20; break }
        catch { Start-Sleep -Seconds 8 }
    }
    if (-not $health) { throw "no answer after about 100 s" }
    if ($health.status -ne "UP") { throw "status is $($health.status)" }
    "UP"
}

Check "Protected route rejects a missing token" {
    $code = StatusOf { Invoke-WebRequest -Uri "$api/api/projects" -UseBasicParsing }
    if ($code -ne 401) { throw "expected 401, got $code" }
    "401"
}

Check "Register" {
    $code = StatusOf { Invoke-WebRequest -Method Post -Uri "$api/api/auth/register" -ContentType "application/json" -UseBasicParsing `
        -Body (@{ username = $username; email = "$username@example.com"; password = $password } | ConvertTo-Json) }
    if ($code -ne 201) { throw "expected 201, got $code" }
    $username
}

Check "Login returns a token" {
    $login = Api "Post" "/api/auth/login" @{ username = $username; password = $password }
    if (-not $login.token) { throw "no token in the response" }
    $script:token = $login.token
    "token received"
}

Check "Create project" {
    $p = Api "Post" "/api/projects" @{ name = "Smoke project"; description = "created by smoke-test.ps1" } $token
    $script:projectId = $p.id
    "id $($p.id)"
}

Check "Create suite and test case" {
    $suite = Api "Post" "/api/projects/$projectId/suites" @{ name = "Smoke suite"; description = "" } $token
    $case = Api "Post" "/api/suites/$($suite.id)/cases" @{
        title = "Smoke case"; description = "d"; priority = "HIGH"; status = "READY"; tags = "smoke"
        steps = @(@{ stepNumber = 1; action = "do it"; expectedResult = "it works" })
    } $token
    "suite $($suite.id), case $($case.id)"
}

Check "List projects" {
    $page = Api "Get" "/api/projects?page=0&size=5" $null $token
    "$($page.totalElements) project(s)"
}

foreach ($endpoint in @("summary", "pass-rate-trend", "defects-by-severity", "recent-activity", "flaky-tests")) {
    Check "Dashboard /$endpoint" {
        [void](Api "Get" "/api/dashboard/${endpoint}?projectId=$projectId" $null $token)
        "200"
    }
}

Check "CORS preflight allows the frontend origin" {
    $r = Invoke-WebRequest -Method Options -Uri "$api/api/projects" -UseBasicParsing -Headers @{
        "Origin" = $web
        "Access-Control-Request-Method" = "GET"
        "Access-Control-Request-Headers" = "authorization"
    }
    $allowed = ($r.Headers["Access-Control-Allow-Origin"] -join ",")
    if ($allowed -ne $web) { throw "Access-Control-Allow-Origin is '$allowed', expected '$web' (set CORS_ALLOWED_ORIGINS on the backend)" }
    $allowed
}

Check "WebSocket handshake accepts the frontend origin" {
    $wsUrl = ($api -replace "^http", "ws") + "/ws"
    $socket = New-Object System.Net.WebSockets.ClientWebSocket
    $socket.Options.SetRequestHeader("Origin", $web)
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter(20000)
    try {
        $socket.ConnectAsync([Uri]$wsUrl, $cts.Token).Wait()
        if ($socket.State -ne "Open") { throw "state is $($socket.State)" }
        "connected to $wsUrl"
    } catch {
        throw "could not open $wsUrl : $($_.Exception.InnerException.Message) (check CORS_ALLOWED_ORIGINS and that the host supports WebSockets)"
    } finally {
        $socket.Dispose()
    }
}

Check "Frontend serves the app" {
    $r = Invoke-WebRequest -Uri $web -UseBasicParsing
    if ($r.StatusCode -ne 200) { throw "HTTP $($r.StatusCode)" }
    if ($r.Content -notmatch 'id="root"') { throw "the page has no #root element" }
    "200"
}

Check "Frontend deep link survives a refresh (SPA fallback)" {
    $code = StatusOf { Invoke-WebRequest -Uri "$web/dashboard" -UseBasicParsing }
    if ($code -ne 200) { throw "expected 200, got $code (add the rewrite to index.html)" }
    "200"
}

if ($projectId) {
    Check "Clean up: delete the smoke project" {
        [void](Api "Delete" "/api/projects/$projectId" $null $token)
        "deleted"
    }
}

$failed = @($results | Where-Object { -not $_.Ok })
Write-Host ""
Write-Host ("{0} passed, {1} failed" -f ($results.Count - $failed.Count), $failed.Count) -ForegroundColor $(if ($failed.Count) { "Red" } else { "Green" })
if ($failed.Count) { exit 1 }
