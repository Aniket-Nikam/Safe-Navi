$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Join-Path $projectRoot "backend"
$pythonPath = Join-Path $backendRoot "venv\Scripts\python.exe"
$databasePath = Join-Path $backendRoot "data\safe_navi_runtime.sqlite"
$apkPath = Join-Path $projectRoot "deliverables\Safe-Navi-Demo-v1.1.apk"
$localPropertiesPath = Join-Path $projectRoot "local.properties"
$adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$healthUrl = "http://127.0.0.1:8000/health"

Write-Host ""
Write-Host "  SAFE-NAVI  |  QUICK ONLINE START" -ForegroundColor Green
Write-Host "  Starting the shared API, news scheduler and phone connection..." -ForegroundColor DarkGray
Write-Host ""

foreach ($required in @($pythonPath, $databasePath, $apkPath)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required Safe-Navi file is missing: $required"
    }
}

$health = $null
try { $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 } catch { }

if ($null -eq $health) {
    $portOwner = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $portOwner) {
        throw "Port 8000 is already occupied by process $($portOwner.OwningProcess), but it is not responding as Safe-Navi. Close that process and retry."
    }

    $stdoutLog = Join-Path $backendRoot "data\server.stdout.log"
    $stderrLog = Join-Path $backendRoot "data\server.stderr.log"
    Start-Process -FilePath $pythonPath `
        -ArgumentList "-m","uvicorn","app.main:app","--host","0.0.0.0","--port","8000" `
        -WorkingDirectory $backendRoot -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog | Out-Null

    for ($attempt = 1; $attempt -le 20 -and $null -eq $health; $attempt++) {
        Start-Sleep -Seconds 1
        try { $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 } catch { }
    }
}

if ($null -eq $health -or $health.status -ne "ok") {
    throw "Safe-Navi did not become healthy. Check backend\data\server.stderr.log."
}

$network = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -ne $null } | Select-Object -First 1
$lanIp = if ($network -and $network.IPv4Address) { $network.IPv4Address.IPAddress } else { "192.168.29.221" }
$phoneHealth = "http://${lanIp}:8000/health"

Write-Host "  Backend              ONLINE" -ForegroundColor Green
Write-Host "  Risk provider         $($health.risk_provider)"
Write-Host "  News scheduler        $($health.news_scheduler)"
Write-Host "  News frequency        every $($health.news_refresh_hours) hours"
Write-Host "  PC API docs           http://127.0.0.1:8000/docs"
Write-Host "  Phone health check    $phoneHealth" -ForegroundColor Cyan
Write-Host ""

if (Test-Path -LiteralPath $localPropertiesPath) {
    $configuredUrl = Select-String -LiteralPath $localPropertiesPath -Pattern '^RISK_API_BASE_URL=(.+)$' |
        Select-Object -First 1 | ForEach-Object { $_.Matches[0].Groups[1].Value.Trim() }
    if ($configuredUrl -and $configuredUrl -notlike "*://${lanIp}:8000") {
        Write-Warning "This APK is configured for $configuredUrl, but the PC currently appears to be $lanIp. Update local.properties and rebuild before using physical phones."
    }
}

if (Test-Path -LiteralPath $adbPath) {
    $device = & $adbPath devices | Select-String "`tdevice$" | Select-Object -First 1
    if ($null -ne $device) {
        Write-Host "  Installing the latest demo APK on the connected Android device..."
        & $adbPath install -r $apkPath | Out-Host
        & $adbPath shell am start -n "com.safenavi.app/.SplashActivity" | Out-Host
        Write-Host "  Android application   LAUNCHED" -ForegroundColor Green
    } else {
        Write-Host "  Android device        none connected (share/install the APK manually)" -ForegroundColor Yellow
    }
}

Start-Process "http://127.0.0.1:8000/docs"
Write-Host ""
Write-Host "Safe-Navi is ready. Keep this PC powered on and connected to the same private Wi-Fi/hotspot as the demo phones." -ForegroundColor Green
Write-Host "Closing this window does not stop the backend." -ForegroundColor DarkGray
