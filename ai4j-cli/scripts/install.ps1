#
# ai4j CLI installer for Windows (PowerShell).
#
# Usage:
#   irm https://raw.githubusercontent.com/LnYo-Cly/ai4j/main/ai4j-cli/scripts/install.ps1 | iex
#   .\install.ps1 -Version v2.4.2
#
param(
    [string]$Version = "latest"
)

$ErrorActionPreference = "Stop"

$Repo = "LnYo-Cly/ai4j"
$InstallDir = if ($env:AI4J_HOME) { $env:AI4J_HOME } else { Join-Path $env:USERPROFILE ".ai4j" }
$BinDir = Join-Path $InstallDir "bin"

function Write-Info($msg)  { Write-Host "==> $msg" -ForegroundColor Green }
function Write-Warn($msg)  { Write-Host "!! $msg" -ForegroundColor Yellow }
function Write-Fatal($msg) { Write-Host "error: $msg" -ForegroundColor Red; exit 1 }

# ---- pre-flight -------------------------------------------------------------

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Fatal "Java is required but 'java' was not found in PATH."
}
$JavaVersionLine = (java -version 2>&1 | Select-Object -First 1).ToString()
Write-Info "Java detected: $JavaVersionLine"

# ---- resolve version --------------------------------------------------------

if ($Version -eq "latest") {
    Write-Info "Fetching latest release version..."
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest"
    $VersionTag = $release.tag_name -replace '^v', ''
} else {
    $VersionTag = $Version -replace '^v', ''
}

Write-Info "Installing ai4j CLI version $VersionTag..."

# ---- download ---------------------------------------------------------------

$JarName = "ai4j-cli-$VersionTag-jar-with-dependencies.jar"
$DownloadUrl = "https://github.com/$Repo/releases/download/v$VersionTag/$JarName"

if (-not (Test-Path $BinDir)) {
    New-Item -ItemType Directory -Path $BinDir -Force | Out-Null
}

$JarPath = Join-Path $BinDir "ai4j.jar"
Write-Info "Downloading $DownloadUrl"
try {
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $JarPath -UseBasicParsing
} catch {
    Write-Fatal "Download failed. Check that version v$VersionTag exists at https://github.com/$Repo/releases"
}

# ---- wrapper script ---------------------------------------------------------

$BatPath = Join-Path $BinDir "ai4j.bat"
$BatContent = @"
@echo off
java %AI4J_JAVA_OPTS% -jar `"$JarPath`" %*
"@
Set-Content -Path $BatPath -Value $BatContent -Encoding ASCII

# ---- done -------------------------------------------------------------------

Write-Host ""
Write-Info "ai4j CLI v$VersionTag installed to $BatPath"
Write-Host ""

$pathHasBin = ($env:PATH -split ';') -contains $BinDir
if (-not $pathHasBin) {
    Write-Warn "Add ai4j to your PATH by running:"
    Write-Host ""
    Write-Host "    [Environment]::SetEnvironmentVariable('PATH', '$BinDir;' + `$env:PATH, 'User')"
    Write-Host ""
}
Write-Info "Run 'ai4j --help' to get started."
