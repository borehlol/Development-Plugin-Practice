# Shared by the other scripts. Not meant to be run on its own.

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$Network = Join-Path $Root 'network'

# Every Docker volume the network keeps data in. Docker names them network_<name>
# because docker-compose.yml sets "name: network".
$Volumes = @('proxy-data', 'lobby-data', 'lifesteal-data', 'kitpvp-data', 'minigame-data', 'mysql-data')

function Write-Step($message) { Write-Host "`n==> $message" -ForegroundColor Cyan }
function Write-Done($message) { Write-Host $message -ForegroundColor Green }

function Fail($message) {
    Write-Host $message -ForegroundColor Red
    exit 1
}

# Runs a program and stops the script if it fails.
function Invoke-Checked {
    param([string]$File, [string[]]$Arguments)
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { Fail "'$File $($Arguments -join ' ')' failed (exit code $LASTEXITCODE)." }
}

function Test-Command($name) { [bool](Get-Command $name -ErrorAction SilentlyContinue) }

# Picks up programs winget just installed without reopening the terminal.
function Update-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
        [Environment]::GetEnvironmentVariable('Path', 'User')
}

# Starts Docker Desktop if needed and waits up to 3 minutes for it.
function Wait-Docker {
    # Windows PowerShell turns redirected error output from a program into a script-stopping error.
    $ErrorActionPreference = 'Continue'
    docker info *> $null
    if ($LASTEXITCODE -eq 0) { return }

    $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (Test-Path $desktop) {
        Write-Host 'Starting Docker Desktop...'
        Start-Process $desktop
    }
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        docker info *> $null
        if ($LASTEXITCODE -eq 0) { return }
    }
    Fail 'Docker is not running. Open Docker Desktop, wait for it to say "Engine running", then try again.'
}

# Runs a throwaway Alpine container with a network volume at /data and a host folder at /backup.
function Invoke-VolumeCommand {
    param([string]$Volume, [string]$HostFolder, [string]$Command)
    Invoke-Checked docker @('run', '--rm', '-v', "network_${Volume}:/data", '-v', "${HostFolder}:/backup",
        'alpine', 'sh', '-c', $Command)
}
