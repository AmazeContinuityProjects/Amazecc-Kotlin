package com.amazecc.app.shared.domain

/**
 * The three fields a day-type classifier reads.
 *
 * VTOP's `CalendarEvent` types `type` as one of three day types. The calendar
 * page has always fed these functions a wider set of bags - Moodle deadlines,
 * exams, OD records and attendance rows all arrive with their own `type` string
 * and their own extra fields - and they worked, because every line below
 * stringifies before comparing. Typing the parameter as what is actually read
 * is the honest version of that.
 */
data class ClassifiableEvent(
    val type: String? = null,
    val text: String? = null,
    val category: String? = null,
)

/** The semester's milestones, and the only vocabulary for them. */
data class ImportantEventName(
    val key: String,
    val display: String,
    val short: String,
    val blurb: String,
    val aliases: List<String> = emptyList(),
    /**
     * Whether classes still run on the day.
     *
     * A CAT or a mid-term is an assessment, so the timetable is replaced - the
     * same rule the exam schedule applies. An LID is the opposite: it is the
     * *last* day of instruction, so it is a day you attend. Reading every
     * milestone as class-free would blank the timetable on the day the student
     * most needs to see it.
     */
    val classesRun: Boolean,
)

/**
 * Day-type classification: holiday vs. non-instructional vs. instructional, and
 * the milestone vocabulary.
 *
 * Ported from `../AmazeCC/src/lib/analyzeCalendar.ts:13-252` - the classifier
 * only, not `analyzeCalendar` itself, which is a separate legacy surface that
 * `AnalyzeCalendar.kt` still carries for the sync path.
 */
object CalendarClassifier {

    private val HOLIDAY_KEYWORDS = listOf(
        "holiday", "pooja", "puja", "ayudha", "diwali", "pongal", "eid", "christmas", "good friday",
        "independence", "republic", "onam", "holi", "ramadan", "ganesh", "maha shivaratri", "vesak",
        "vacation", "term end",
        // The calendar page historically carried a longer list than this file. It is
        // the same vocabulary plus the festival names VTOP lists under `text`, so
        // the two copies were already agreeing; they are now one list.
        //
        // "no instructional" is deliberately NOT here. A non-instructional day is
        // not a holiday - the college is open and staff are on campus, there is
        // simply no teaching. It used to be in this list, which is how a working
        // day came to be reported as a day off. See [isNonInstructionalEvent].
        "vinayakar chathurthi", "gandhi jayanthi", "thaipoosam", "telugu", "tamil", "ambedkar",
    )

    /**
     * The vocabulary VTOP uses for "campus is open, no classes today".
     *
     * Split out from [HOLIDAY_KEYWORDS] because these three are different facts:
     * a day with no classes, a day the college is shut, and a day with a
     * shortened list. Collapsing the first into the second is what made the old
     * calendar paint a red day for a normal working day.
     */
    private val NON_INSTRUCTIONAL_KEYWORDS = listOf(
        "no instructional", "non instructional", "noninstructional", "non instructional day",
        "no class", "no classes", "no teaching", "instructors retreat", "student senate",
    )

    /**
     * Lower-case, strip punctuation, and collapse whitespace runs to one space.
     *
     * Delegates to [ExamSeries.looseNormalise]; kept as a named entry point
     * because the day-type classifiers and the milestone matcher both read
     * through it, and it is the rule that decides whether a working day is a day
     * off.
     */
    fun normalize(str: String?): String = ExamSeries.looseNormalise(str ?: "")

    fun isHolidayEvent(e: ClassifiableEvent?): Boolean {
        if (e == null) return false
        val type = (e.type ?: "").lowercase()
        val text = normalize(e.text ?: "")
        val cat = normalize(e.category ?: "")
        if (type.contains("holiday")) return true
        for (kw in HOLIDAY_KEYWORDS) {
            if (text.contains(kw) || cat.contains(kw)) return true
        }
        return false
    }

    /**
     * Campus open, no teaching.
     *
     * Checked *before* [isInstructionalEvent], and it has to be: VTOP writes
     * "Non Instructional Day" with a category of "Working day", so the
     * instructional test's `category.contains("working")` also matches it.
     * Whichever runs first wins, and getting this order wrong reports a working
     * day as teaching.
     */
    fun isNonInstructionalEvent(e: ClassifiableEvent?): Boolean {
        if (e == null) return false
        if (isHolidayEvent(e)) return false
        val type = (e.type ?: "").lowercase()
        val text = normalize(e.text ?: "")
        val cat = normalize(e.category ?: "")
        for (kw in NON_INSTRUCTIONAL_KEYWORDS) {
            if (type.contains(kw) || text.contains(kw) || cat.contains(kw)) return true
        }
        return false
    }

    /**
     * Whether the day is an instructional one.
     *
     * All three fields are consulted, because the real payload spreads the
     * signal: a teaching day arrives as `text: "Instructional Day"`,
     * `category: "Working Day"`, and - on the days that carry a day order or a
     * FAT run - as `category: "Working Day / LAB FAT"`. Checking `type` and
     * `category` alone misses `"Instructional Day Order - Friday Day Order"`
     * entirely, because its type is `"Other"` and its category never says
     * "working", so that day came back as non-instructional: no classes, college
     * open, timetable gone.
     *
     * The non-instructional guard is what makes reading `text` safe here - "No
     * Instructional Day" contains the same words in the opposite sense.
     */
    fun isInstructionalEvent(e: ClassifiableEvent?): Boolean {
        if (e == null) return false
        if (isNonInstructionalEvent(e)) return false
        val type = (e.type ?: "").lowercase()
        val text = normalize(e.text ?: "")
        val cat = normalize(e.category ?: "")
        if (type == "instructional day") return true
        if (cat.contains("working")) return true
        return text.contains("instructional day")
    }

    /**
     * The semester's milestones.
     *
     * These are the dates a student actually plans around - when the continuous
     * tests are, when instruction stops. VTOP publishes them as ordinary
     * calendar entries with no distinguishing type, so they are found by text
     * match, and that match has to live in one place: the calendar page
     * classifies days by it and this table indexes them by it, and two copies
     * would drift the first time a name was edited.
     *
     * [ImportantEventName.display] is load-bearing and must not be reworded:
     * callers look milestones up by exact string against it. [short] is the
     * label for a tile or a list row, where the full uppercase name is too long
     * to read.
     */
    val IMPORTANT_EVENTS: List<ImportantEventName> = listOf(
        ImportantEventName(
            key = "cat i",
            display = "CAT I",
            short = "CAT I",
            blurb = "Continuous Assessment Test I",
            classesRun = false,
        ),
        ImportantEventName(
            key = "cat ii",
            display = "CAT II",
            short = "CAT II",
            blurb = "Continuous Assessment Test II",
            classesRun = false,
        ),
        ImportantEventName(
            key = "lid for laboratory classes",
            display = "LID FOR LABORATORY CLASSES",
            short = "LID — Lab",
            blurb = "Last instructional day for laboratory classes",
            aliases = listOf("lid for lab"),
            classesRun = true,
        ),
        ImportantEventName(
            key = "lid for theory classes",
            display = "LID FOR THEORY CLASSES",
            short = "LID — Theory",
            blurb = "Last instructional day for theory classes",
            classesRun = true,
        ),
        ImportantEventName(
            key = "mid term test",
            display = "MID TERM TEST",
            short = "Mid Term Test",
            blurb = "Mid Term Test begins",
            classesRun = false,
        ),
    )

    /**
     * The milestone an event names, if any.
     *
     * Both `text` and `category` are searched, `text` first. That is not
     * belt-and-braces: the academic calendar's day-type entries are
     * `"Instructional Day"` in `text` with the milestone in `category` -
     * `"Working Day / LID for LAB classes"`. Reading `text` alone loses every
     * LID date on the calendar.
     *
     * The two fields are searched separately rather than concatenated, so a
     * `text` of "CAT" and a `category` of "II" cannot combine across the
     * boundary into a CAT II that VTOP never wrote.
     */
    fun matchImportantEvent(e: ClassifiableEvent?): ImportantEventName? {
        if (e == null) return null
        return matchImportantText(e.text) ?: matchImportantText(e.category)
    }

    private fun matchImportantText(text: String?): ImportantEventName? {
        if (normalize(text).isEmpty()) return null
        val needles = IMPORTANT_EVENTS.flatMap { listOf(it.key) + it.aliases }
        val best = ExamSeries.longestCanonicalMatch(text, needles) ?: return null
        return IMPORTANT_EVENTS.firstOrNull { it.key == best || best in it.aliases }
    }

    /** A single tidy spelling for a series name, for a row or a badge. */
    fun prettySeriesName(raw: String?): String {
        matchImportantEvent(ClassifiableEvent(text = raw))?.let { return it.short }
        val cleaned = ExamSeries.looseNormalise(raw)
        if (cleaned.isEmpty()) return "Exam"
        return cleaned.replaceFirstChar { it.uppercaseChar() }
    }
}
