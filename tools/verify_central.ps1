# ============================================================================
# CENTRALIZED VERIFICATION RUNNER - the ONLY supported way to run the build.
# Workers must NEVER invoke gradlew/gradle directly. Only the orchestrator
# calls this script. See docs/BUILD_RUNNER.md for the contract.
#
# Usage:
#   powershell -File tools/verify_central.ps1 [-LogName <name>] [-Clean]
#
# -LogName  basename for <name>.log / <name>.exit under %USERPROFILE%
# -Clean    add --rerun-tasks to flush stale incremental analysis
# ============================================================================
param(
    [string]$LogName = "verify_central",
    [switch]$Clean
)

# NOTE: do NOT set ErrorActionPreference=Stop. In PowerShell 5.1 a native
# command's stderr becomes a terminating NativeCommandError, which aborts the
# script before the log/exit files are written and before this report prints.
$ErrorActionPreference = "Continue"

$env:JAVA_HOME = "C:\Users\sugee\.gradle\jdks\microsoft-17-amd64-windows.2"
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$log = Join-Path $env:USERPROFILE "$LogName.log"
$exitFile = Join-Path $env:USERPROFILE "$LogName.exit"

# Build the argument list as an explicit [string[]] so a single switch is never
# unwrapped into a string and splatted character-by-character (PowerShell 5.1).
[string[]]$gradleArgs = @(
    ":shared:jvmTest",
    ":androidApp:compileDebugKotlin",
    "--console=plain",
    "--no-parallel"
)
if ($Clean) { $gradleArgs = $gradleArgs + "--rerun-tasks" }

Write-Output "============================================================================"
Write-Output "CENTRAL BUILD RUNNER"
Write-Output "  started   : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Output "  repo      : $repo"
Write-Output "  JAVA_HOME : $env:JAVA_HOME"
Write-Output "  log       : $log"
Write-Output "  args      : $($gradleArgs -join ' ')"
Write-Output "============================================================================"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
& .\gradlew.bat @gradleArgs *> $log
$code = $LASTEXITCODE
$sw.Stop()

"$code" | Out-File $exitFile
Write-Output "EXIT: $code   (elapsed $([math]::Round($sw.Elapsed.TotalSeconds,1))s)"

# ----------------------------------------------------------------------------
# Detailed report: read the full log, strip ANSI, break out every compiler
# diagnostic with its full candidate list, every task failure, and the test
# summary. Nothing is truncated to a fixed line count.
# ----------------------------------------------------------------------------
if (-not (Test-Path $log)) {
    Write-Output "NO LOG PRODUCED at $log"
    exit $code
}

$raw = Get-Content $log -Raw
$cleanText = $raw -replace '\x1b\[[0-9;]*m', ''
$lines = $cleanText -split "`r?`n"

Write-Output ""
Write-Output "----------------------------------------------------------------------------"
Write-Output "TASK OUTCOMES"
Write-Output "----------------------------------------------------------------------------"
$taskLines = $lines | Where-Object { $_ -match '^> Task ' -and $_ -match 'FAILED|FROM-CACHE|UP-TO-DATE|NO-SOURCE' }
if ($taskLines) { $taskLines | ForEach-Object { Write-Output $_ } } else { Write-Output "(none)" }

# Collect compiler diagnostics: a line beginning with 'e: ' plus all following
# continuation lines until the next diagnostic / task / failure marker.
Write-Output ""
Write-Output "----------------------------------------------------------------------------"
Write-Output "COMPILER DIAGNOSTICS (full, grouped by file)"
Write-Output "----------------------------------------------------------------------------"
$diags = New-Object System.Collections.Generic.List[object]
$cur = $null
$stopMarkers = '^(e: |> Task |FAILURE:|\* What went wrong:|BUILD (SUCCESSFUL|FAILED)|[0-9]+ actionable tasks)'
foreach ($ln in $lines) {
    if ($ln -match '^e: ') {
        if ($cur) { $diags.Add($cur) }
        $cur = New-Object PSObject -Property @{ Text = [System.Collections.Generic.List[string]]@($ln) }
    } elseif ($cur -and $ln -match $stopMarkers) {
        $diags.Add($cur); $cur = $null
    } elseif ($cur) {
        $cur.Text.Add($ln)
    }
}
if ($cur) { $diags.Add($cur) }

if ($diags.Count -eq 0) {
    Write-Output "(no compiler diagnostics found in log)"
} else {
    $groups = $diags | Group-Object {
        $m = [regex]::Match($_.Text[0], '([A-Za-z0-9_/]+\.kt):(\d+):(\d+)')
        if ($m.Success) { $m.Groups[1].Value } else { 'unknown' }
    }
    Write-Output "total diagnostics: $($diags.Count)   unique files: $($groups.Count)"
    foreach ($g in $groups) {
        Write-Output ""
        Write-Output "### $($g.Name)  [$($g.Count) diagnostic(s)]"
        foreach ($d in $g.Group) {
            $d.Text | ForEach-Object {
                $t = $_.Trim()
                if ($t) { Write-Output "    $t" }
            }
        }
    }
}

Write-Output ""
Write-Output "----------------------------------------------------------------------------"
Write-Output "GRADLE FAILURE SECTION"
Write-Output "----------------------------------------------------------------------------"
$failureIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\* What went wrong:|^\* Try:|^FAILURE: Build failed') { $failureIdx = $i; break }
}
if ($failureIdx -ge 0) {
    $lines[$failureIdx..([Math]::Min($failureIdx + 40, $lines.Count - 1))] | ForEach-Object { Write-Output $_ }
} else {
    Write-Output "(no explicit failure section)"
}

Write-Output ""
Write-Output "----------------------------------------------------------------------------"
Write-Output "TEST / BUILD SUMMARY LINES"
Write-Output "----------------------------------------------------------------------------"
$summary = $lines | Where-Object {
    $_ -match 'BUILD (SUCCESSFUL|FAILED)|tests? completed|tests? failed|FAILED$|actionable tasks|> Task :shared:jvmTest|> Task :androidApp:compileDebugKotlin'
}
if ($summary) { $summary | ForEach-Object { Write-Output $_ } } else { Write-Output "(none)" }

Write-Output ""
Write-Output "FULL LOG: $log"
Write-Output "EXIT FILE: $exitFile"
exit $code
