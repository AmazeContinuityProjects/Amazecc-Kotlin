package com.amazecc.app.shared.vtop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/** Outcome of one diagnostic probe. */
data class VtopDiagnosticResult(
    val name: String,
    val status: VtopDiagnosticStatus,
    val detail: String = "",
    val durationMs: Long = 0
)

enum class VtopDiagnosticStatus { PASS, FAIL, SKIP, RUNNING }

/** One probe the user can trigger from Settings. */
data class VtopDiagnosticCheck(
    val name: String,
    val run: suspend () -> VtopDiagnosticResult
)

/**
 * On-device health checks for the local VTOP layer.
 *
 * Deliberately user-initiated and one-shot. The `vtop_source` switch is manual by design, so
 * when the local path breaks there is nothing telling the user why — these probes are the
 * compensation, and they double as the escape hatch: if the per-module checks fail, switching
 * back to `REMOTE` is a one-tap fix.
 */
object VtopDiagnostics {

    /** Fast probe so a broken network fails in seconds rather than hanging the screen. */
    private const val PROBE_TIMEOUT_MS = 8_000L

    private suspend fun timed(
        name: String,
        block: suspend () -> VtopDiagnosticResult
    ): VtopDiagnosticResult = try {
        val started = Clock.System.now().toEpochMilliseconds()
        val result = block()
        result.copy(
            name = name,
            durationMs = Clock.System.now().toEpochMilliseconds() - started
        )
    } catch (t: Throwable) {
        VtopDiagnosticResult(name, VtopDiagnosticStatus.FAIL, t.message ?: t.toString())
    }

    fun checks(): List<VtopDiagnosticCheck> = listOf(
        VtopDiagnosticCheck("Platform support") {
            if (vtopEngineSupported) {
                VtopDiagnosticResult("", VtopDiagnosticStatus.PASS, "On-device VTOP available")
            } else {
                VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.FAIL,
                    "Not available on this platform. Use the Remote source."
                )
            }
        },
        VtopDiagnosticCheck("Session tokens") {
            val csrf = VtopSession.csrf.value
            val id = VtopSession.authorizedID.value
            when {
                csrf.isNullOrBlank() || id.isNullOrBlank() ->
                    VtopDiagnosticResult("", VtopDiagnosticStatus.FAIL, "Not logged in to VTOP")
                else -> VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.PASS,
                    "authorizedID=${id.take(6)}***, csrf present"
                )
            }
        },
        VtopDiagnosticCheck("Semester selected") {
            val subId = VtopSession.semesterSubId.value
            if (subId.isNullOrBlank()) {
                VtopDiagnosticResult("", VtopDiagnosticStatus.FAIL, "No semester selected after login")
            } else {
                VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.PASS,
                    "${VtopSession.semesterName.value ?: "?"} ($subId)"
                )
            }
        },
        VtopDiagnosticCheck("VTOP reachable") {
            val csrf = VtopSession.csrf.value?.takeIf { it.isNotBlank() }
                ?: return@VtopDiagnosticCheck VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.SKIP, "No CSRF token yet"
                )
            val body = "verifyMenu=true&authorizedID=${VtopSession.authorizedID.value}" +
                "&_csrf=$csrf&nocache=${Clock.System.now().toEpochMilliseconds()}"
            val script = VtopScripts.fetchRows(
                path = "/vtop/examinations/examGradeView/StudentGradeHistory",
                body = body,
                selector = "tr",
                tableSelector = "#fixedTableContainer table",
                tableIndex = 1
            )
            val result = VtopRows.parse(
                withContext(Dispatchers.Default) {
                    Vtop.engineInstance().evaluate(script, timeoutMs = PROBE_TIMEOUT_MS)
                }
            )
            when {
                !result.ok && result.error == "not authorized" -> VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.FAIL,
                    "VTOP rejected the User-Agent. Try again to rotate it."
                )
                !result.ok -> VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.FAIL, "Fetch failed: ${result.error}"
                )
                else -> VtopDiagnosticResult("", VtopDiagnosticStatus.PASS, "Grade history responded")
            }
        },
        VtopDiagnosticCheck("User-Agent") {
            VtopDiagnosticResult("", VtopDiagnosticStatus.PASS, UserAgentPool.current().take(72))
        },
        VtopDiagnosticCheck("WebView engine") {
            val cookies = withContext(Dispatchers.Default) {
                Vtop.engineInstance().cookieHeader()
            }
            if (cookies.isBlank()) {
                VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.FAIL, "No cookies in the WebView jar"
                )
            } else {
                VtopDiagnosticResult(
                    "", VtopDiagnosticStatus.PASS, "Cookie jar holds ${cookies.length} chars"
                )
            }
        }
    )

    /** Per-module checks. These make real VTOP calls, so they run only when explicitly asked. */
    fun moduleChecks(): List<VtopDiagnosticCheck> = listOf(
        VtopDiagnosticCheck("Timetable") {
            moduleProbe { sem ->
                val res = VtopDataSource.fetchTimetable(sem)
                val count = res.courseInfo?.size ?: 0
                res.success to (if (count > 0) "$count courses" else (res.message ?: "no data"))
            }
        },
        VtopDiagnosticCheck("Attendance") {
            moduleProbe { sem ->
                val res = VtopDataSource.fetchAttendance(sem, includeDetail = false)
                val count = res.attendance?.size ?: 0
                res.success to (if (count > 0) "$count courses" else (res.message ?: "no data"))
            }
        },
        VtopDiagnosticCheck("Marks") {
            moduleProbe { sem ->
                val res = VtopDataSource.fetchMarks(sem)
                    ?: return@moduleProbe false to "no response from VTOP"
                val count = res.courses.size
                res.success to (if (count > 0) "$count courses" else (res.message ?: "no data"))
            }
        },
        VtopDiagnosticCheck("Grades") { gradesProbe() },
        VtopDiagnosticCheck("Hostel") { objectProbe { VtopDataSource.fetchHostel() } },
        VtopDiagnosticCheck("Payments") { objectProbe { VtopDataSource.fetchPayments() } }
    )

    private fun semester(): String? =
        VtopSession.semesterSubId.value?.takeIf { it.isNotBlank() }

    private suspend inline fun moduleProbe(
        crossinline block: suspend (String) -> Pair<Boolean, String>
    ): VtopDiagnosticResult {
        val sem = semester() ?: return VtopDiagnosticResult(
            "", VtopDiagnosticStatus.SKIP, "No semester selected"
        )
        val (ok, detail) = block(sem)
        return VtopDiagnosticResult(
            "", if (ok) VtopDiagnosticStatus.PASS else VtopDiagnosticStatus.FAIL, detail
        )
    }

    private suspend fun gradesProbe(): VtopDiagnosticResult {
        val res = VtopDataSource.fetchGrades()
            ?: return VtopDiagnosticResult("", VtopDiagnosticStatus.FAIL, "No response")
        val count = res.effectiveGrades?.size ?: 0
        return VtopDiagnosticResult(
            "",
            if (res.success) VtopDiagnosticStatus.PASS else VtopDiagnosticStatus.FAIL,
            if (count > 0) "$count grade rows" else (res.error ?: "No grade rows")
        )
    }

    private suspend inline fun objectProbe(
        crossinline block: suspend () -> Any?
    ): VtopDiagnosticResult {
        val res = block() ?: return VtopDiagnosticResult(
            "", VtopDiagnosticStatus.FAIL, "No response from VTOP"
        )
        return VtopDiagnosticResult("", VtopDiagnosticStatus.PASS, "Responded (${res::class.simpleName})")
    }

    /** Runs [check] and always returns a non-RUNNING result, even on an unexpected throw. */
    suspend fun run(check: VtopDiagnosticCheck): VtopDiagnosticResult = timed(check.name) {
        check.run()
    }
}
