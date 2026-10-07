# Replaces the network's worlds, plugin data, ranks, and network/.env with a backup from backup.ps1.
# Usage: restore <path to backup .zip>     (with no path, uses the newest file in backups/)

param([string]$Backup)

. (Join-Path $PSScriptRoot 'common.ps1')

if (-not $Backup) {
    $newest = Get-ChildItem (Join-Path $Root 'backups') -Filter 'backup-*.zip' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $newest) { Fail 'No backup given and none found in backups/. Usage: restore <path to backup .zip>' }
    $Backup = $newest.FullName
}
if (-not (Test-Path $Backup)) { Fail "Can't find $Backup" }
$Backup = (Resolve-Path $Backup).Path

Write-Host "This REPLACES everything on this machine's network with:" -ForegroundColor Yellow
Write-Host "  $Backup"
Write-Host 'Worlds, ranks, and plugin data made since that backup will be lost.' -ForegroundColor Yellow
if ((Read-Host 'Type yes to continue') -ne 'yes') { Fail 'Cancelled, nothing was changed.' }

Wait-Docker

$staging = Join-Path $env:TEMP "skinny-restore-$(Get-Date -Format 'yyyyMMddHHmmss')"
Write-Step 'Unzipping'
Expand-Archive -Path $Backup -DestinationPath $staging
foreach ($volume in $Volumes) {
    if (-not (Test-Path (Join-Path $staging "$volume.tar.gz"))) { Fail "The backup is missing $volume.tar.gz, nothing was changed." }
}

Push-Location $Network
try {
    Write-Step 'Stopping the network'
    # "create" also makes the volumes on a machine that has never run the network.
    Invoke-Checked docker @('compose', 'create')
    Invoke-Checked docker @('compose', 'stop')

    foreach ($volume in $Volumes) {
        Write-Step "Restoring $volume"
        Invoke-VolumeCommand $volume $staging ("find /data -mindepth 1 -delete && " +
            "tar xzf /backup/$volume.tar.gz -C /data")
    }
    Copy-Item (Join-Path $staging 'env') (Join-Path $Network '.env') -Force

    if (-not (Test-Path (Join-Path $Network 'plugins\SkinnyPlugin.jar'))) {
        Write-Step 'Building the plugin'
        Push-Location $Root
        try { Invoke-Checked (Join-Path $Root 'gradlew.bat') @('deployNetwork', '--console=plain', '--quiet') }
        finally { Pop-Location }
    }

    Write-Step 'Starting the network'
    Invoke-Checked docker @('compose', 'up', '-d')
}
finally {
    Pop-Location
    Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Done 'Restored. The servers need a minute or two to finish starting.'
