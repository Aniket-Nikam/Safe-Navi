$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Join-Path $projectRoot "backend"
$pythonPath = Join-Path $backendRoot "venv\Scripts\python.exe"
$databasePath = Join-Path $backendRoot "data\safe_navi_runtime.sqlite"
$adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $databasePath)) {
    throw "Baseline risk database is missing: $databasePath"
}
if (-not (Test-Path -LiteralPath $pythonPath)) {
    py -3.11 -m venv (Join-Path $backendRoot "venv")
}
& $pythonPath -c "import fastapi, uvicorn" 2>$null
if ($LASTEXITCODE -ne 0) {
    & $pythonPath -m pip install -r (Join-Path $backendRoot "requirements.txt")
}

$health = $null
try { $health = Invoke-RestMethod -Uri "http://127.0.0.1:8000/health" -TimeoutSec 2 } catch {}
if ($null -eq $health) {
    Start-Process -FilePath $pythonPath `
        -ArgumentList "-m","uvicorn","app.main:app","--host","0.0.0.0","--port","8000" `
        -WorkingDirectory $backendRoot -WindowStyle Hidden | Out-Null
    Start-Sleep -Seconds 3
}

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Push-Location $projectRoot
try {
    .\gradlew.bat assembleDebug
    if (Test-Path -LiteralPath $adbPath) {
        $device = & $adbPath devices | Select-String "\tdevice$" | Select-Object -First 1
        if ($null -ne $device) {
            & $adbPath install -r "app\build\outputs\apk\debug\app-debug.apk"
            & $adbPath shell am start -n "com.safenavi.app/.SplashActivity"
        } else {
            Write-Host "APK built. Start an emulator and run this script again to install it."
        }
    }
} finally {
    Pop-Location
}
Write-Host "Safe-Navi API: http://127.0.0.1:8000/docs"
