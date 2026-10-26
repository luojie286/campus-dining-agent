param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'backend')
try {
    $javaVersionText = (& java --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $javaVersionText -notmatch '(?m)^(?:openjdk|java) 21(?:\.|\s)') {
        throw 'JDK 21 is required. Set JAVA_HOME and PATH to JDK 21 before starting this project.'
    }
    if (-not $SkipBuild) {
        & mvn.cmd -B -ntp package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
    }
    $jarPath = Join-Path (Get-Location).Path 'sky-server\target\sky-server-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build the server JAR first' }
    & java '-Dfile.encoding=UTF-8' -jar $jarPath
    if ($LASTEXITCODE -ne 0) { throw 'Backend failed to start; check its log and profile' }
} finally { Pop-Location }
