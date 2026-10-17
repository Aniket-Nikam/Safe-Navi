param([switch]$ConfirmPublicSharing)
$ErrorActionPreference = 'Stop'
# Deliberately opt-in: this exposes the authenticated API and public map data to the internet.
if (-not $ConfirmPublicSharing) {
    Write-Host 'This creates a temporary public HTTPS URL for the running Safe-Navi backend.'
    Write-Host 'Keep the laptop awake. Rotate shared keys/passwords before use. Stop with Ctrl+C.'
    Write-Host 'Run again with -ConfirmPublicSharing to confirm internet exposure.'
    exit 1
}
$health = Invoke-RestMethod -Uri 'http://127.0.0.1:8000/health' -TimeoutSec 5
if ($health.status -ne 'ok') { throw 'Start Safe-Navi first; local health check did not pass.' }
$tunnel = Get-Command cloudflared -ErrorAction SilentlyContinue
if (-not $tunnel) { throw 'Install cloudflared from https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/ and run again.' }
Write-Host 'Copy the https://....trycloudflare.com URL below into Server connection on EVERY phone.'
Write-Host 'This URL changes when the tunnel restarts. Existing sessions may need sign-in again.'
& $tunnel.Source tunnel --url http://127.0.0.1:8000 --no-autoupdate
