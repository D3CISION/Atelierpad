$ErrorActionPreference = 'Stop'
$Version = '8.11.1'
$Parallel = if ($env:ATELIER_PARALLEL) { $env:ATELIER_PARALLEL } else { 'true' }
$Parallel = $Parallel.ToLowerInvariant()
if ($Parallel -notin @('true','false')) { throw 'ATELIER_PARALLEL must be true or false.' }
$Root = Split-Path $PSScriptRoot -Parent
$Base = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
if (-not $env:ANDROID_HOME) {
    $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (-not (Test-Path $Sdk)) { throw 'Install Android SDK 35 and Build Tools 35.0.0 in Android Studio. Set ANDROID_HOME to the SDK folder.' }
    $env:ANDROID_HOME = $Sdk
}
if (-not $env:JAVA_HOME) {
    $Jbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path $Jbr) { $env:JAVA_HOME = $Jbr; $env:PATH = "$Jbr\bin;$env:PATH" }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'JDK 17 or 21 is required. Set JAVA_HOME.' }
$Cache = Join-Path $env:USERPROFILE '.gradle\atelier-bootstrap'
$Gradle = Join-Path $Cache "gradle-$Version\bin\gradle.bat"
if (-not (Test-Path $Gradle)) {
    New-Item -ItemType Directory -Force $Cache | Out-Null
    $Zip = Join-Path $Cache "gradle-$Version.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing $Base -OutFile $Zip
    $ChecksumFile = "$Zip.sha256"
    Invoke-WebRequest -UseBasicParsing "$Base.sha256" -OutFile $ChecksumFile
    $Expected = (Get-Content -Raw $ChecksumFile).Trim().ToLowerInvariant()
    Remove-Item $ChecksumFile
    if ($Expected -notmatch '^[a-f0-9]{64}$') { throw 'Invalid upstream checksum.' }
    $Actual = (Get-FileHash -Algorithm SHA256 $Zip).Hash.ToLowerInvariant()
    if ($Actual -ne $Expected) { Remove-Item $Zip; throw 'Gradle checksum mismatch. Aborting.' }
    Expand-Archive -Path $Zip -DestinationPath $Cache -Force
    Remove-Item $Zip
}
Push-Location $Root
try {
    & $Gradle --no-daemon wrapper --gradle-version $Version --distribution-type bin
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper generation failed.' }
    & $Gradle --no-daemon "-PatelierSideBySide=$Parallel" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed. See the error output above.' }
    Write-Host "APK: $Root\app\build\outputs\apk\debug\app-debug.apk"
    Write-Host "Side-by-side install: $Parallel"
    Write-Host 'Android instrumented tests are compiled but not run by this script.'
} finally { Pop-Location }
