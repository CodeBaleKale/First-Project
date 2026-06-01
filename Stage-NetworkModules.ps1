<#
.SYNOPSIS
    Downloads PowerShell troubleshooting modules (with dependencies) to a local
    folder so they can be copied to USB and sideloaded onto an offline/air-gapped
    server. RUN THIS ON A MACHINE WITH INTERNET.

.DESCRIPTION
    Uses Save-Module to pull each module from the PowerShell Gallery as plain
    files (no install, no admin needed on this machine). Also bundles the
    matching install instructions and a manifest so the offline box install is
    copy-paste simple. Read-only to your system except writing the output folder.

    Modules staged (all open-source, auditable on GitHub):
      - Posh-SSH         : SSH/SFTP/SCP automation (script the switch collection)
      - PoshSNMP         : native SNMP get/walk (fills the 'no-tool' gap)
      - PSNetAddressing  : subnet/CIDR math, generate IP target lists

.PARAMETER OutPath
    Folder to stage into. Default: .\OfflineModules

.PARAMETER Modules
    Module names to stage. Default: the three above.

.EXAMPLE
    .\Stage-NetworkModules.ps1
    .\Stage-NetworkModules.ps1 -OutPath D:\usb\PSModules

.NOTES
    SAFETY: downloads files only. Does NOT install anything on this machine.
    Requires: internet + PowerShellGet (built into PS 5.1 / 7.x).
#>

[CmdletBinding()]
param(
    [string]$OutPath = $(if ($PSScriptRoot) { Join-Path $PSScriptRoot 'OfflineModules' } else { Join-Path (Get-Location) 'OfflineModules' }),
    [string[]]$Modules = @('Posh-SSH','PoshSNMP','PSNetAddressing')
)

$ErrorActionPreference = 'Stop'

Write-Host ""
Write-Host "PowerShell Module Stager (for offline/USB install)" -ForegroundColor Cyan
Write-Host ("  Output : {0}" -f $OutPath)
Write-Host ("  Modules: {0}" -f ($Modules -join ', '))
Write-Host ""

# ---- 1. Make sure we can reach the Gallery and have a modern NuGet provider ----
try {
    # TLS 1.2 is required to talk to the PowerShell Gallery on older boxes
    [System.Net.ServicePointManager]::SecurityProtocol = `
        [System.Net.ServicePointManager]::SecurityProtocol -bor `
        [System.Net.SecurityProtocolType]::Tls12
} catch {
    Write-Warning "Could not set TLS 1.2; if downloads fail, that is the likely cause."
}

# Ensure NuGet provider exists (Save-Module needs it). Installs to CURRENT USER only.
$nuget = Get-PackageProvider -Name NuGet -ErrorAction SilentlyContinue
if (-not $nuget -or $nuget.Version -lt [version]'2.8.5.201') {
    Write-Host "Installing NuGet package provider (current user)..." -ForegroundColor Yellow
    try {
        Install-PackageProvider -Name NuGet -MinimumVersion 2.8.5.201 -Force -Scope CurrentUser | Out-Null
    } catch {
        Write-Warning "NuGet provider install failed. If Save-Module fails below, run PowerShell as admin once and retry."
    }
}

# Make sure PSGallery is registered
if (-not (Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue)) {
    Write-Host "Registering PSGallery repository..." -ForegroundColor Yellow
    Register-PSRepository -Default -ErrorAction SilentlyContinue
}

# ---- 2. Create the output folder ----
New-Item -ItemType Directory -Path $OutPath -Force | Out-Null

# ---- 3. Save each module (WITH dependencies) ----
$results = @()
foreach ($m in $Modules) {
    Write-Host ("Staging {0} ..." -f $m) -ForegroundColor Green
    try {
        # Save-Module pulls the module AND its dependencies as plain folders.
        Save-Module -Name $m -Path $OutPath -Repository PSGallery -Force -ErrorAction Stop
        # Record what version(s) landed
        $saved = Get-ChildItem -Path (Join-Path $OutPath $m) -Directory -ErrorAction SilentlyContinue |
                 Select-Object -ExpandProperty Name
        $results += [pscustomobject]@{ Module = $m; Status = 'OK'; Versions = ($saved -join ', ') }
        Write-Host ("  done: {0} (version {1})" -f $m, ($saved -join ', ')) -ForegroundColor Green
    } catch {
        $results += [pscustomobject]@{ Module = $m; Status = 'FAILED'; Versions = $_.Exception.Message }
        Write-Warning ("  FAILED {0}: {1}" -f $m, $_.Exception.Message)
    }
}

# ---- 4. Drop install instructions into the staged folder ----
$instr = @'
OFFLINE INSTALL INSTRUCTIONS
============================
These folders are PowerShell modules saved with Save-Module. To install them
on the offline server, copy this whole folder there, then place each module
folder into a PowerShell module path.

STEP 1 - copy this entire folder onto the USB, then onto the server (e.g. C:\Staged).

STEP 2 - on the server, pick a module path. The all-users path is:
   C:\Program Files\WindowsPowerShell\Modules        (Windows PowerShell 5.1)
   C:\Program Files\PowerShell\Modules               (PowerShell 7.x, if installed)
Per-user (no admin) alternative:
   $HOME\Documents\WindowsPowerShell\Modules         (PS 5.1)
   $HOME\Documents\PowerShell\Modules                (PS 7.x)

STEP 3 - copy each MODULE FOLDER (Posh-SSH, PoshSNMP, PSNetAddressing) from this
staged folder into the module path you chose. The structure must end up as:
   ...\Modules\Posh-SSH\<version>\Posh-SSH.psd1
Save-Module already creates that <version> subfolder, so copy the named folder
(e.g. "Posh-SSH") as-is.

STEP 4 - unblock the files (they came from the internet). In an elevated PS prompt:
   Get-ChildItem 'C:\Program Files\WindowsPowerShell\Modules\Posh-SSH' -Recurse | Unblock-File
   Get-ChildItem 'C:\Program Files\WindowsPowerShell\Modules\PoshSNMP' -Recurse | Unblock-File
   Get-ChildItem 'C:\Program Files\WindowsPowerShell\Modules\PSNetAddressing' -Recurse | Unblock-File

STEP 5 - verify:
   Get-Module -ListAvailable Posh-SSH, PoshSNMP, PSNetAddressing
   Import-Module Posh-SSH ; Get-Command -Module Posh-SSH | Select-Object -First 5

NOTES
- Posh-SSH on Windows Server 1709/older needs .NET Framework 4.8+. Server 2016
  ships with 4.6.2. Check before importing:
     (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full').Release
  Release >= 528040 means 4.8 is present. If lower, .NET 4.8 must be installed
  first (separate offline installer from Microsoft).
- Execution policy: if Import-Module is blocked, in that session run:
     Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
- Everything here is open-source; review the .psm1/.ps1 files before installing
  on a controlled network if your baseline requires it.
'@
$instr | Out-File -FilePath (Join-Path $OutPath 'INSTALL_OFFLINE.txt') -Encoding ASCII

# ---- 5. Write a manifest of exactly what was staged ----
$manifest = $results | Format-Table -AutoSize | Out-String -Width 4096
$manifest | Out-File -FilePath (Join-Path $OutPath 'MANIFEST.txt') -Encoding ASCII
Get-ChildItem -Path $OutPath -Directory | ForEach-Object {
    $sz = (Get-ChildItem $_.FullName -Recurse -File | Measure-Object Length -Sum).Sum
    "{0,-20} {1,10:N0} KB" -f $_.Name, ($sz/1KB)
} | Out-File -FilePath (Join-Path $OutPath 'MANIFEST.txt') -Encoding ASCII -Append

# ---- 6. Summary ----
Write-Host ""
Write-Host "=== STAGING SUMMARY ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize | Out-String -Width 4096 | Write-Host
Write-Host ("Staged folder: {0}" -f $OutPath) -ForegroundColor Green
Write-Host "Next: copy that whole folder to USB, then follow INSTALL_OFFLINE.txt on the server." -ForegroundColor Green
Write-Host ""

# ---- 7. Optional: also zip it for easy USB transfer ----
try {
    $zip = "$OutPath.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path (Join-Path $OutPath '*') -DestinationPath $zip -Force
    Write-Host ("Also zipped: {0}" -f $zip) -ForegroundColor Green
} catch {
    Write-Warning "Could not auto-zip (Compress-Archive may be unavailable). The folder is still ready to copy."
}
