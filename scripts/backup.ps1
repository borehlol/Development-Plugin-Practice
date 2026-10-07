# Saves every world, all plugin data, the MySQL ranks, and network/.env into backups/backup-<date>.zip.
# The network is stopped for the minute this takes, so nothing is copied while it's being written.

. (Join-Path $PSScriptRoot 'common.ps1')

Wait-Docker

$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
$backups = Join-Path $Root 'backups'
$staging = Join-Path $env:TEMP "skinny-backup-$stamp"
$zip = Join-Path $backups "backup-$stamp.zip"
New-Item -ItemType Directory -Force $backups, $staging | Out-Null

Push-Location $Network
try {
    $running = docker compose ps --services --status running
    if ($running) {
        Write-Step 'Stopping the network'
        Invoke-Checked docker @('compose', 'stop')
    }

    try {
        foreach ($volume in $Volumes) {
            Write-Step "Saving $volume"
            # Paper, Velocity, and Paper's libraries download again on their own when a server starts.
            Invoke-VolumeCommand $volume $staging ("tar czf /backup/$volume.tar.gz -C /data " +
                "--exclude=./libraries --exclude=./versions --exclude=./cache " +
                "--exclude=./paper-*.jar --exclude=./velocity-*.jar .")
        }
        # The MySQL data only opens with the passwords it was created with, so they travel together.
        Copy-Item (Join-Path $Network '.env') (Join-Path $staging 'env')
    }
    finally {
        if ($running) {
            Write-Step 'Starting the network again'
            Invoke-Checked docker @('compose', 'up', '-d')
        }
    }
}
finally { Pop-Location }

Write-Step 'Zipping'
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zip -Force
Remove-Item -Recurse -Force $staging

$size = '{0:N0} MB' -f ((Get-Item $zip).Length / 1MB)
Write-Host ''
Write-Done "Saved $zip ($size)"
Write-Host 'It contains your passwords, so keep it private.'
