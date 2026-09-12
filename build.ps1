param([string[]]$Tasks = @('testDebugUnitTest','lintDebug','assembleRelease'))
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:/Program Files/Java/jdk-17' }
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
$env:ANDROID_HOME = Join-Path $PSScriptRoot '.tools/android-sdk'
$env:ANDROID_USER_HOME = Join-Path $PSScriptRoot '.tools/android-user'
& "$PSScriptRoot/.tools/gradle-8.11.1/bin/gradle.bat" @Tasks --console=plain
exit $LASTEXITCODE
