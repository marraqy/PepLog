$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if ((Test-Path -LiteralPath 'signing.properties') -or (Test-Path -LiteralPath 'private/release.p12')) {
    throw 'Existing signing material found. It must not be overwritten.'
}
New-Item -ItemType Directory -Path 'private' -Force | Out-Null
$keyBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($keyBytes) } finally { $rng.Dispose() }
$keyPassword = [Convert]::ToBase64String($keyBytes)
$env:PEPTIDES_KEY_PASSWORD = $keyPassword
try {
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:/Program Files/Java/jdk-17' }
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore 'private/release.p12' -storetype PKCS12 -alias peptides-release -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Personal Peptide Journal' -storepass:env PEPTIDES_KEY_PASSWORD -keypass:env PEPTIDES_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    @("storeFile=private/release.p12", "storePassword=$keyPassword", 'keyAlias=peptides-release', "keyPassword=$keyPassword") | Set-Content -LiteralPath 'signing.properties' -Encoding ASCII
} finally { Remove-Item Env:PEPTIDES_KEY_PASSWORD }
Write-Output 'Signing key created. Keep private/release.p12 and signing.properties together in a secure backup.'
