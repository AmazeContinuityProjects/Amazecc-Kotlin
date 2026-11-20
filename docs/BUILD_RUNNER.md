# Centralized build runner (mandatory)

**Only the mother/orchestrator runs builds.** No worker/subagent may invoke `gradlew`,
`gradle`, or any compile/test task directly. Competing builds on the same working tree
corrupt outputs and waste cycles; this is why the previous wave stalled.

## The single build command

```powershell
powershell -File tools/verify_central.ps1 [-LogName <name>]
```

`tools/verify_central.ps1` is the canonical runner. It:
- pins `JAVA_HOME` to `C:\Users\sugee\.gradle\jdks\microsoft-17-amd64-windows.2`
- runs `:shared:jvmTest :androidApp:compileDebugKotlin --no-parallel`
- writes `<LogName>.log` and `<LogName>.exit` under `%USERPROFILE%`
- prints a filtered error summary on failure

## Worker contract

1. Workers edit **only** their assigned files and never run a build.
2. Workers do lightweight self-checks (view diffs, grep) and report D-IDs + files + risks.
3. The mother runs `tools/verify_central.ps1` once per wave (serially), triages errors,
   and dispatches precise fixes to the owning worker.
4. Mother commits D-by-D only after the central build is green.

## Model

All swarm workers run on `opencode/hy4-flash-free` (this session's model). Workers on other
models hit the free usage limit immediately; do not spawn them on other models.
