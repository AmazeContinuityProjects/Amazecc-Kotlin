# Wave verification gate: runs the user's required verification command.
$env:JAVA_HOME = "C:\Users\sugee\.gradle\jdks\microsoft-17-amd64-windows.2"
.\gradlew.bat :shared:jvmTest :androidApp:compileDebugKotlin --console=plain -q > "$env:USERPROFILE\verify_run.log" 2>&1
$code = $LASTEXITCODE
$code > "$env:USERPROFILE\verify_run.exit"
Get-Content "$env:USERPROFILE\verify_run.log" -ErrorAction SilentlyContinue | Select-String -Pattern "FAILED|error:|e: |BUILD " | Select-Object -First 24
"EXIT: $code"
