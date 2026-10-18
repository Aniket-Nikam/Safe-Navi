param([switch]$FirewallSetupOnly)

$ErrorActionPreference = "Stop"

function Test-SafeNaviFirewall {
    $rule = Get-NetFirewallRule -Name "SafeNaviDemoApi" -ErrorAction SilentlyContinue
    if ($null -eq $rule) { return $false }
    $port = $rule | Get-NetFirewallPortFilter
    $address = $rule | Get-NetFirewallAddressFilter
    return ($rule.Enabled -eq "True" -and $rule.Direction -eq "Inbound" -and
        $rule.Action -eq "Allow" -and $rule.Profile -eq "Private" -and
        $port.Protocol -eq "TCP" -and $port.LocalPort -eq "8000" -and
        @($address.RemoteAddress).Count -eq 1 -and $address.RemoteAddress -eq "LocalSubnet")
}

if ($FirewallSetupOnly) {
    try {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object Security.Principal.WindowsPrincipal($identity)
        if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            throw "Firewall setup requires administrator access."
        }
        $rule = Get-NetFirewallRule -Name "SafeNaviDemoApi" -ErrorAction SilentlyContinue
        if ($null -eq $rule) {
            New-NetFirewallRule -Name "SafeNaviDemoApi" -DisplayName "Safe-Navi Demo API" `
                -Direction Inbound -Action Allow -Enabled True -Protocol TCP -LocalPort 8000 `
                -Profile Private -RemoteAddress LocalSubnet | Out-Null
        } else {
            $rule | Set-NetFirewallRule -Direction Inbound -Action Allow -Enabled True -Profile Private
            $rule | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol TCP -LocalPort 8000
            $rule | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress LocalSubnet
        }
        if (-not (Test-SafeNaviFirewall)) { throw "Firewall rule verification failed." }
        exit 0
    } catch {
        Write-Error $_
        exit 1
    }
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Join-Path $projectRoot "backend"
$pythonPath = Join-Path $backendRoot "venv\Scripts\python.exe"
$databasePath = Join-Path $backendRoot "data\safe_navi_runtime.sqlite"
$apkPath = Join-Path $projectRoot "deliverables\Safe-Navi-1.5.apk"
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

if (-not (Test-SafeNaviFirewall)) {
    Write-Host "  Setting up private-network phone access. Accept the Windows administrator prompt." -ForegroundColor Yellow
    try {
        $helper = Start-Process -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" `
            -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -FirewallSetupOnly" `
            -Verb RunAs -WindowStyle Hidden -Wait -PassThru
    } catch {
        throw "Firewall setup was cancelled or failed. Restart this launcher and accept the Windows administrator prompt. $($_.Exception.Message)"
    }
    if ($helper.ExitCode -ne 0 -or -not (Test-SafeNaviFirewall)) {
        throw "Private-network firewall setup failed. Safe-Navi cannot confirm phone access on TCP port 8000."
    }
}
Write-Host "  Phone firewall        READY (Private network, local subnet, TCP 8000)" -ForegroundColor Green

$profiles = Get-NetConnectionProfile -ErrorAction SilentlyContinue
if (-not ($profiles | Where-Object { $_.NetworkCategory -eq "Private" })) {
    Write-Warning "Set your trusted demo Wi-Fi/hotspot to Private in Windows Network settings so the phone-access rule applies."
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

Write-Host "  APK                   Map, community & news 1.5 (cloud default; existing server choice preserved)"
Write-Host "  To use THIS laptop's accounts/data: in the app open Server connection, enter http://${lanIp}:8000, then Test and save." -ForegroundColor Cyan

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
