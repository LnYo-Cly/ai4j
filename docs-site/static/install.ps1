$ErrorActionPreference = "Stop"

function Write-Info {
    param([string]$Message)
    Write-Host $Message
}

function Fail {
    param([string]$Message)
    throw "ai4j installer: $Message"
}

function Test-SkipPathUpdate {
    $value = $env:AI4J_SKIP_PATH_UPDATE
    if (-not $value) {
        return $false
    }

    return @("1", "true", "yes") -contains $value.ToLowerInvariant()
}

function Resolve-Version {
    if ($env:AI4J_VERSION) {
        return $env:AI4J_VERSION
    }

    $repo = if ($env:AI4J_MAVEN_REPO) { $env:AI4J_MAVEN_REPO.TrimEnd('/') } else { "https://repo.maven.apache.org/maven2" }
    $metadataUrl = "$repo/io/github/lnyo-cly/ai4j-cli/maven-metadata.xml"
    [xml]$xml = (Invoke-WebRequest -UseBasicParsing -Uri $metadataUrl).Content
    $version = $xml.metadata.versioning.release
    if (-not $version) {
        $version = $xml.metadata.versioning.latest
    }
    if (-not $version) {
        Fail "unable to resolve latest ai4j-cli version from Maven metadata"
    }
    return $version
}

function Ensure-Java {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        Fail "Java 8+ is required. Install Java first, then rerun this installer."
    }

    $firstLine = (& java -version 2>&1 | Select-Object -First 1)
    if ($firstLine -notmatch '"([^"]+)"') {
        Fail "unable to detect Java version"
    }

    $version = $Matches[1]
    if ($version.StartsWith("1.")) {
        $major = [int]($version.Split(".")[1])
    } else {
        $major = [int]($version.Split(".")[0])
    }

    if ($major -lt 8) {
        Fail "Java 8+ is required. Current Java major version: $major"
    }
}

function Ensure-Path {
    param([string]$BinDir)

    if (Test-SkipPathUpdate) {
        Write-Info "Skipping PATH update because AI4J_SKIP_PATH_UPDATE is set."
        return
    }

    $currentUserPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $segments = @()
    if ($currentUserPath) {
        $segments = $currentUserPath.Split(";") | Where-Object { $_ -and $_.Trim() }
    }

    $normalized = $segments | ForEach-Object { $_.TrimEnd('\') }
    if ($normalized -contains $BinDir.TrimEnd('\')) {
        if (-not (($env:Path.Split(";") | ForEach-Object { $_.TrimEnd('\') }) -contains $BinDir.TrimEnd('\'))) {
            $env:Path = "$BinDir;$env:Path"
        }
        Write-Info "ai4j is already available on PATH for the current user."
        return
    }

    $newPath = if ($currentUserPath) { "$currentUserPath;$BinDir" } else { $BinDir }
    [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    $env:Path = "$BinDir;$env:Path"
    Write-Info "Added $BinDir to the user PATH."
    Write-Info "Open a new terminal if 'ai4j' is not found immediately."
}

function Main {
    Ensure-Java

    $repo = if ($env:AI4J_MAVEN_REPO) { $env:AI4J_MAVEN_REPO.TrimEnd('/') } else { "https://repo.maven.apache.org/maven2" }
    $version = Resolve-Version
    $ai4jHome = if ($env:AI4J_HOME) { $env:AI4J_HOME } else { Join-Path $HOME ".ai4j" }
    $binDir = Join-Path $ai4jHome "bin"
    $libDir = Join-Path $ai4jHome "lib"
    $jarUrl = "$repo/io/github/lnyo-cly/ai4j-cli/$version/ai4j-cli-$version-jar-with-dependencies.jar"
    $jarPath = Join-Path $libDir "ai4j-cli.jar"
    $tmpJar = Join-Path $libDir "ai4j-cli.jar.tmp"
    $versionFile = Join-Path $ai4jHome "version.txt"
    $cmdPath = Join-Path $binDir "ai4j.cmd"

    Write-Info "Installing ai4j-cli $version"
    New-Item -ItemType Directory -Force -Path $binDir | Out-Null
    New-Item -ItemType Directory -Force -Path $libDir | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $jarUrl -OutFile $tmpJar
    Move-Item -Force $tmpJar $jarPath
    Set-Content -Path $versionFile -Value $version -Encoding Ascii

    $cmdContent = @"
@echo off
setlocal
set "INSTALL_HOME=$ai4jHome"
if not defined AI4J_HOME set "AI4J_HOME=%INSTALL_HOME%"
set "JAVA_BIN=java"
if defined AI4J_JAVA set "JAVA_BIN=%AI4J_JAVA%"
if not exist "%AI4J_HOME%\lib\ai4j-cli.jar" (
  echo ai4j launcher: missing "%AI4J_HOME%\lib\ai4j-cli.jar" 1>&2
  exit /b 1
)
if defined AI4J_JAVA_OPTS (
  %JAVA_BIN% %AI4J_JAVA_OPTS% -jar "%AI4J_HOME%\lib\ai4j-cli.jar" %*
  set "EXIT_CODE=%ERRORLEVEL%"
) else (
  %JAVA_BIN% -jar "%AI4J_HOME%\lib\ai4j-cli.jar" %*
  set "EXIT_CODE=%ERRORLEVEL%"
)
endlocal & exit /b %EXIT_CODE%
"@
    Set-Content -Path $cmdPath -Value $cmdContent -Encoding Ascii

    Ensure-Path -BinDir $binDir

    Write-Info ""
    Write-Info "Installed ai4j-cli $version to $ai4jHome"
    Write-Info "Then run: ai4j --help"
}

Main
