<#
Minimal, robust helper to activate Temurin 21 for the current PowerShell session.
It does not modify profile files; it uses jabba which to get the JDK path.
#>

function Get-JavaMajorVersion {
    try {
        $out = & java -version 2>&1 | Out-String
        $m = [regex]::Match($out, '(\d+)')
        if ($m.Success) { return [int]$m.Groups[1].Value }
    } catch {}
    return 0
}

$maj = Get-JavaMajorVersion
if ($maj -ge 21) {
    Write-Output 'Java 21 already active in this session'
    exit 0
}

if (Get-Command jabba -ErrorAction SilentlyContinue) {
    Write-Output 'jabba found - querying path for temurin@1.21.0-10'
    $jdkPath = $null
    try { $jdkPath = (& jabba which temurin@1.21.0-10 2>$null) } catch {}
    if (-not $jdkPath) {
        try { $jdkPath = (& jabba which temurin@1.21 2>$null) } catch {}
    }
    if ($jdkPath -and (Test-Path $jdkPath)) {
        $env:JAVA_HOME = $jdkPath
        $env:Path = "$jdkPath\bin;$env:Path"
        Write-Output "Set JAVA_HOME=$jdkPath"
        $newMaj = Get-JavaMajorVersion
        if ($newMaj -ge 21) {
            Write-Output 'Activated Temurin 21 via jabba'
            & java -version
            exit 0
        }
    }
}

Write-Output "Java 21 not active (detected major version: $maj)."
Write-Output 'To activate Temurin 21 for this session:'
Write-Output ' - Install Temurin 21 (Adoptium) or use your package manager.'
Write-Output ' - Or install jabba and run: jabba install temurin@1.21'
Write-Output ' - Or set the variables for this session:'
Write-Output '     $env:JAVA_HOME = "C:\path\to\temurin-21"'
Write-Output '     $env:Path = "$env:JAVA_HOME\bin;" + $env:Path'
Write-Output 'This script does not modify your profile files.'
exit 1
