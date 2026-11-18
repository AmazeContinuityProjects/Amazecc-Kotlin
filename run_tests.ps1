# Run shared module JVM unit and UI tests without needing an Android emulator or device
param(
    [string]$tests = ""
)

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $candidates = @(
        "C:\Users\sugee\AppData\Local\Programs\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
    )
    foreach ($cand in $candidates) {
        if (Test-Path "$cand\bin\java.exe") {
            $env:JAVA_HOME = $cand
            break
        }
    }
}

if (-not $env:JAVA_HOME) {
    Write-Error "JAVA_HOME could not be located. Please install JDK 17+ or Android Studio."
    exit 1
}

$gradleArgs = @(":shared:jvmTest")
if ($tests -ne "") {
    $gradleArgs += "--tests"
    $gradleArgs += $tests
}

Write-Host "Running JVM tests with JAVA_HOME=$env:JAVA_HOME..." -ForegroundColor Cyan
& .\gradlew.bat @gradleArgs
