<#
.SYNOPSIS
    Activates Temurin 21 for the current session via jabba.

.DESCRIPTION
    Sets JAVA_HOME and prepends the JDK bin directory to PATH for this session only.
    Does not modify any profile files.

    Version detection reads the JDK's 'release' file (JAVA_VERSION="21.x.y") instead
    of executing 'java -version', which is unreliable for shim-based installs (Scoop).

    Safe to dot-source (. .\use-temurin-21.ps1) — uses `return` instead of `exit`.

.EXAMPLE
    . .\use-temurin-21.ps1    # dot-source: env vars persist in current shell
    .\use-temurin-21.ps1      # child process: env vars scoped to that process
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# Helper: reads major version from a JDK 'release' file.
# Every JDK >= 9 ships $JAVA_HOME\release containing: JAVA_VERSION="21.0.3"
# Returns 0 if the file is missing or unparseable.
# ---------------------------------------------------------------------------
function Get-MajorVersionFromRelease {
    param([string]$JdkRoot)
    $releaseFile = Join-Path $JdkRoot 'release'
    if (-not (Test-Path $releaseFile)) { return 0 }
    try {
        $line  = Get-Content $releaseFile | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        $match = [regex]::Match($line, 'JAVA_VERSION="?(\d+)')
        if ($match.Success) { return [int]$match.Groups[1].Value }
    }
    catch { }
    return 0
}

# ---------------------------------------------------------------------------
# Helper: reads major version from whatever 'java' is currently on PATH.
# Used only for the initial "already active?" check.
# ---------------------------------------------------------------------------
function Get-ActiveJavaMajorVersion {
    try {
        $out   = & java -version 2>&1 | Out-String
        $match = [regex]::Match($out, 'version "(\d+)')
        if ($match.Success) { return [int]$match.Groups[1].Value }
    }
    catch { }
    return 0
}

# ---------------------------------------------------------------------------
# Helper: idempotently prepends a directory to PATH.
# [regex]::Escape makes the check safe for paths that contain @ . \ etc.
# ---------------------------------------------------------------------------
function Add-ToPath {
    param([string]$Directory)
    if ($env:Path -notmatch [regex]::Escape($Directory)) {
        $env:Path = "$Directory;$env:Path"
    }
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

# Fast exit: Java 21+ is already active on PATH.
$activeMajor = Get-ActiveJavaMajorVersion
if ($activeMajor -ge 21) {
    Write-Output "Java $activeMajor already active in this session."
    & java -version
    return
}

if (-not (Get-Command jabba -ErrorAction SilentlyContinue)) {
    Write-Warning 'jabba not found on PATH.'
}
else {
    Write-Output 'jabba found — resolving Temurin 21 path...'

    $jdkRoot = $null

    foreach ($alias in @('temurin@1.21.0-10', 'temurin@1.21')) {
        try {
            $raw = & jabba which $alias 2>$null
            if (-not $raw) { continue }

            $candidate = $raw.Trim()
            if (-not (Test-Path $candidate)) {
                Write-Warning "jabba returned '$candidate' for '$alias' but path does not exist."
                continue
            }

            $jdkRoot = $candidate
            break
        }
        catch { }
    }

    if ($jdkRoot) {
        # Validate the JDK using the 'release' file — works with shims, junctions,
        # and any layout where executing java.exe directly would fail or lie.
        $major = Get-MajorVersionFromRelease -JdkRoot $jdkRoot

        if ($major -eq 0) {
            Write-Warning "No valid 'release' file found under '$jdkRoot'."
            Write-Warning "Expected: $jdkRoot\release  with a line like: JAVA_VERSION=`"21.0.x`""
            Write-Warning 'Run: jabba install temurin@1.21'
        }
        elseif ($major -lt 21) {
            Write-Warning "JDK at '$jdkRoot' is major version $major, not 21+."
            Write-Warning 'Run: jabba install temurin@1.21'
        }
        else {
            $binDir = Join-Path $jdkRoot 'bin'
            $env:JAVA_HOME = $jdkRoot
            Add-ToPath $binDir

            Write-Output "Activated Temurin $major via jabba."
            Write-Output "JAVA_HOME = $jdkRoot"

            # Final confirmation: try the now-PATH-aware java.
            # If it still fails (shim edge case) we still succeeded — env vars are set.
            try   { & java -version }
            catch { Write-Warning "'java -version' failed but JAVA_HOME and PATH are set correctly." }

            return
        }
    }
    else {
        Write-Warning "jabba could not resolve a valid Temurin 21 path (tried temurin@1.21.0-10 and temurin@1.21)."
        Write-Warning 'Run: jabba install temurin@1.21'
    }
}

# ---------------------------------------------------------------------------
# Fallback instructions
# ---------------------------------------------------------------------------
Write-Output ''
Write-Output 'Java 21 could not be activated. Options:'
Write-Output ''
Write-Output '  1. Reinstall via jabba:'
Write-Output '       jabba install temurin@1.21'
Write-Output '       . .\use-temurin-21.ps1'
Write-Output ''
Write-Output '  2. Install Temurin 21 from https://adoptium.net, then:'
Write-Output '       $env:JAVA_HOME = "C:\path\to\temurin-21"'
Write-Output '       $env:Path      = "$env:JAVA_HOME\bin;" + $env:Path'
Write-Output ''
Write-Output 'This script does not modify any profile files.'