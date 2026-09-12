param([string]$Serial = 'emulator-5556')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This script clears test data and only accepts an emulator serial.' }
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:/Program Files/Java/jdk-17' }
if (-not $env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = "$PSScriptRoot/.tools/android-user" }
$adb = "$PSScriptRoot/.tools/android-sdk/platform-tools/adb.exe"
$signer = "$PSScriptRoot/.tools/android-sdk/build-tools/35.0.0/apksigner.bat"
$line = Get-Content -LiteralPath 'signing.properties' | Where-Object { $_.StartsWith('storePassword=') }
$env:PEPTIDES_TEST_KEY = $line.Substring('storePassword='.Length)
try {
    & $signer sign --ks private/release.p12 --ks-key-alias peptides-release --ks-pass env:PEPTIDES_TEST_KEY --key-pass env:PEPTIDES_TEST_KEY --out .tools/release-test.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test signing failed.' }
} finally { Remove-Item Env:PEPTIDES_TEST_KEY }
& $adb -s $Serial install -r app/build/outputs/apk/release/app-release.apk
if ($LASTEXITCODE -ne 0) { throw 'Release install failed. Remove any debug installation from the test emulator first.' }
& $adb -s $Serial install -r .tools/release-test.apk
if ($LASTEXITCODE -ne 0) { throw 'Test runner install failed.' }
& $adb -s $Serial shell pm clear app.peptides.journal
if ($LASTEXITCODE -ne 0) { throw 'Could not clear emulator test data.' }
$result = & $adb -s $Serial shell am instrument -w app.peptides.journal.test/androidx.test.runner.AndroidJUnitRunner
$result | Tee-Object -FilePath artifacts/release-device-tests.txt
New-Item -ItemType Directory -Path artifacts/screenshots -Force | Out-Null
foreach ($capture in @('home-en.png','syringe-picker-en.png','calculation-en.png','record-en.png','today-en.png','settings-pt.png','history-pt.png','catalog-pt.png','protocol-agenda-en.png','protocol-agenda-pt.png','protocol-empty-en.png','protocol-item-en.png','protocol-calendar-en.png','protocol-calendar-states-en.png','blend-calculation-en.png','blend-today-pt.png','time-picker-en.png','reminder-notification-en.png')) {
    & $adb -s $Serial pull "/sdcard/Android/data/app.peptides.journal/files/$capture" "artifacts/screenshots/$capture"
}
if (($result -join "`n") -notmatch 'OK \(14 tests\)') { throw 'Android release tests failed. See artifacts/release-device-tests.txt.' }
