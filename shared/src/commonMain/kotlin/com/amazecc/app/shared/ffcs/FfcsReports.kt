package com.amazecc.app.shared.ffcs

/**
 * D-176 — semester/report registry and report path keys.
 *
 * Ported from `src/lib/ffcsReports.ts:11-86, 213-226` as documented by the
 * `ffcs-planner` skill `reference/03-data-model-and-schema.md:126-137` and
 * `reference/05-csv-generation.md:9-23`:
 *
 *  - winter final is the default and shares the CH202627050 class-id namespace
 *    with the winter mock (the numeric share format uses a separate prefix
 *    CODE to tell them apart),
 *  - fall uses CH202627010,
 *  - `previous` is an alias for fall, kept so an old plan keeps resolving,
 *  - CodeChef has no class ids and cannot resolve share links.
 *
 * Nothing here fetches anything: [path] values are the public asset keys the
 * UI loader uses, not network calls made by this pure module.
 */

/** Wire ids from the web `FFCSSemesterId` union. */
enum class FfcsSemesterId(val value: String) {
    WINTER_FINAL("winter-2026-2027"),
    WINTER_MOCK("winter-2026-2027-mock"),
    FALL("fall-2026-2027"),
    PREVIOUS("previous"),
    CODECHEF("codechef-vit");

    companion object {
        fun from(value: String?): FfcsSemesterId? =
            entries.firstOrNull { it.value == value }

        /** `previous` is a real id on the wire even though it is not a report row. */
        fun isWireId(value: String?): Boolean = from(value) != null
    }
}

/** One row of the web `FFCS_SEMESTER_REPORTS` registry. */
data class FfcsSemesterReport(
    val id: FfcsSemesterId,
    val label: String,
    val shortLabel: String,
    /** Public report path key, e.g. `/ffcs/winter-2026-2027-final-ffcs.csv`. */
    val path: String,
    val classIdPrefix: String? = null,
    /** Stable numeric alias used by the shortest class-id share format. */
    val classIdPrefixCode: String? = null,
    val name: String,
    val attribution: String? = null,
)

object FfcsReports {

    // Report path keys — the exact asset paths from the web registry.
    const val PATH_WINTER_FINAL: String = "/ffcs/winter-2026-2027-final-ffcs.csv"
    const val PATH_WINTER_MOCK: String = "/ffcs/winter-2026-2027-slot-timetable.csv"
    const val PATH_FALL_FINAL: String = "/ffcs/fall-2026-2027-final-ffcs.csv"
    const val PATH_CODECHEF: String = "/ffcs/codechef-vit-catalogue.csv"

    /** localStorage key that stores the chosen semester id. */
    const val STORAGE_KEY: String = "ffcs_semester"

    /** Winter/mock class-id namespace (CH202627050…). */
    const val PREFIX_WINTER: String = "CH202627050"
    /** Fall (and `previous`) class-id namespace (CH202627010…). */
    const val PREFIX_FALL: String = "CH202627010"

    val DEFAULT_SEMESTER: FfcsSemesterId = FfcsSemesterId.WINTER_FINAL

    val SEMESTER_REPORTS: List<FfcsSemesterReport> = listOf(
        FfcsSemesterReport(
            id = FfcsSemesterId.WINTER_FINAL,
            label = "Winter Semester 2026-2027",
            shortLabel = "Winter 2026-27",
            path = PATH_WINTER_FINAL,
            classIdPrefix = PREFIX_WINTER,
            classIdPrefixCode = "2",
            name = "Winter Semester 2026-2027 final FFCS course list",
            attribution = "Course data from VIT's Winter 2026-2027 course offered list.",
        ),
        FfcsSemesterReport(
            id = FfcsSemesterId.WINTER_MOCK,
            label = "Winter 2026-27 Mock List",
            shortLabel = "Winter Mock",
            path = PATH_WINTER_MOCK,
            classIdPrefix = PREFIX_WINTER,
            classIdPrefixCode = "1",
            name = "Winter Semester 2026-2027 mock slot timetable",
            attribution = "Mock course data published before VIT's final Winter 2026-2027 course offered list.",
        ),
        FfcsSemesterReport(
            id = FfcsSemesterId.FALL,
            label = "Fall Semester 2026-2027",
            shortLabel = "Fall 2026-27",
            path = PATH_FALL_FINAL,
            classIdPrefix = PREFIX_FALL,
            classIdPrefixCode = "0",
            name = "Fall Semester 2026-2027 final FFCS course list",
            attribution = "Course data from VIT's Fall 2026-2027 course allocation report.",
        ),
        FfcsSemesterReport(
            id = FfcsSemesterId.CODECHEF,
            label = "CodeChef VIT Catalogue",
            shortLabel = "CodeChef VIT",
            path = PATH_CODECHEF,
            name = "CodeChef VIT offline catalogue",
            attribution = "Course & faculty data converted from FFCS-inator by CodeChef VIT — ffcs.codechefvit.com (MIT).",
        ),
    )

    /** Report path keys, stable for persistence and deep links. */
    val REPORT_PATHS: Map<FfcsSemesterId, String> =
        SEMESTER_REPORTS.associate { it.id to it.path }

    fun isFfcsSemesterId(value: String?): Boolean = FfcsSemesterId.isWireId(value)

    /**
     * `previous` resolves to fall; an unknown id falls back to the default
     * winter final, matching `getFFCSSemester` (`ffcsReports.ts:219-226`).
     */
    fun getFfcsSemester(id: String?): FfcsSemesterReport {
        val target = when (FfcsSemesterId.from(id)) {
            FfcsSemesterId.PREVIOUS -> FfcsSemesterId.FALL
            else -> FfcsSemesterId.from(id)
        }
        return SEMESTER_REPORTS.firstOrNull { it.id == target }
            ?: SEMESTER_REPORTS.first { it.id == DEFAULT_SEMESTER }
    }

    fun getFfcsSemester(id: FfcsSemesterId): FfcsSemesterReport = when (id) {
        FfcsSemesterId.PREVIOUS -> getFfcsSemester(FfcsSemesterId.FALL)
        else -> SEMESTER_REPORTS.firstOrNull { it.id == id }
            ?: SEMESTER_REPORTS.first { it.id == DEFAULT_SEMESTER }
    }

    /**
     * Longest-prefix wins, so a future longer namespace still resolves. A tie
     * (winter final vs winter mock share CH202627050) resolves to the default
     * exactly as the web registry does — the full class ids are byte-identical
     * under both reports.
     */
    fun getFfcsSemesterByClassIdPrefix(prefix: String): FfcsSemesterReport? {
        val matches = SEMESTER_REPORTS.filter { report ->
            report.classIdPrefix?.let { prefix.startsWith(it) } == true
        }
        val longest = matches.maxOfOrNull { it.classIdPrefix?.length ?: 0 } ?: return null
        return matches.firstOrNull { it.classIdPrefix?.length == longest && it.id == DEFAULT_SEMESTER }
            ?: matches.first { it.classIdPrefix?.length == longest }
    }
}
