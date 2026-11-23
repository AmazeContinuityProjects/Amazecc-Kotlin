package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.utils.TimeMath

/**
 * D-172 — canonical FFCS slot vocabulary and clash algebra.
 *
 * Port of the contract described in the `ffcs-planner` skill:
 *  - `reference/03-data-model-and-schema.md:337-440` (schema, `timeToMinutes`,
 *    `periodsForSlot`, overlap predicate, lunch, `−30` same-day/same-half penalty)
 *  - `reference/05-csv-generation.md:76-120` (lab windows derived from
 *    `labTokensOf()` + `SLOTS_BY_PERIOD`, not literal arrays)
 *
 * Provenance / no invented slots:
 *  The [CHENNAI] projection is *generated* from [SlotMap.map], the existing
 *  day -> token -> `"H:MM-H:MM"` source data. Every token in [CHENNAI] therefore
 *  already exists in `SlotMap.kt`; nothing is hand-invented. The task brief's
 *  "A1..A60" is shorthand for the full theory vocabulary (A1/A2, B1/B2, ... TA/TAA,
 *  S11/S15, weekend X/Y/Z/W tokens) — there is no literal `A60` token in the source
 *  data, so none is emitted. Lab tokens are the real `L1..L60` (weekday) and
 *  `L71..L94` (weekend) keys; `L01..L13` are *period ids* used only to derive the
 *  lab-window sets via [SlotMap.labTokensOf].
 *
 * This module is deliberately self-contained in the `ffcs` package. The older
 * `com.amazecc.app.shared.domain.Slots` port remains untouched; both describe the
 * same real data, and this one is the FFCS generator's canonical copy.
 */

/** One row of the schema: a clock range plus the token each day uses for it. */
data class SchemaPeriod(
    val start: String = "",
    val end: String = "",
    val days: Map<String, String> = emptyMap(),
    val lunch: Boolean = false,
)

/** One concrete (day, clock) occurrence of a slot token. */
data class SlotPeriod(
    val day: String,
    val start: String,
    val end: String,
    val startMin: Int,
    val endMin: Int,
    val type: String, // "theory" | "lab"
    val index: Int,
)

/** `{ theory: List<SchemaPeriod>, lab: List<SchemaPeriod> }` — skill reference/03 §3.1. */
data class CampusSchema(
    val theory: List<SchemaPeriod> = emptyList(),
    val lab: List<SchemaPeriod> = emptyList(),
)

/**
 * Strict schema clock parser (skill `slots.ts:timeToMinutes`, reference/03 §3.1).
 *
 * Accepts `H:MM AM|PM` / `HH:MM AM|PM`; a **missing meridian is invalid** (unlike
 * the web-parity `TimeMath.parseTimeToMinutes`, which accepts a bare 24h clock).
 * This is a top-level function in the `ffcs` package so the existing `TimeMath`
 * signature is unchanged; it delegates to the new strict entry point added there.
 */
fun parseTimeToMinutes(time: String?): Int? = TimeMath.parseSchemaTimeToMinutes(time)

object FfcsSlotSchema {

    /** Pairing-score constant from reference/05 §3.1 — data, not applied here. */
    const val SAME_DAY_SAME_HALF_PENALTY: Int = -30

    /** 2:00 PM — the morning/evening boundary used for shift classification. */
    const val MORNING_BEFORE_MIN: Int = 840

    val CHENNAI: CampusSchema = buildChennaiSchema()

    private val DAY_IDS: Set<String> =
        setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    private val LAW_BY_SCHEMA = mapOf("S11" to "TEE", "S15" to "TFF")
    private val SCHEMA_BY_LAW = mapOf("TEE" to "S11", "TFF" to "S15")
    private val NOT_A_LAW_SLOT = setOf("NIL", "S", "L")

    /** Upper-case and trim a token; `null`/blank becomes `""`. */
    fun normaliseSlot(slot: String?): String = slot?.trim()?.uppercase() ?: ""

    /** `"a1+ta1"` -> `["A1", "TA1"]`; splits on `+`, upper-cases and trims. */
    fun splitSlots(slot: String?): List<String> {
        if (slot.isNullOrBlank()) return emptyList()
        return slot.split("+")
            .map { normaliseSlot(it) }
            .filter { it.isNotEmpty() }
    }

    /**
     * Every id naming the same period as [slot], including itself.
     * Aliases: `A1<->A`, `C1<->C`, `E1<->E`, `TAA1<->TAA`, `S11<->TEE`, `S15<->TFF`.
     * `NIL`, `S` and `L` are excluded from alias expansion (they name no period).
     */
    fun slotSpellings(slot: String?): Set<String> {
        val id = normaliseSlot(slot)
        if (id.isEmpty()) return emptySet()
        val out = mutableSetOf(id)

        LAW_BY_SCHEMA[id]?.let { out.add(it) } // S11 -> TEE, S15 -> TFF
        Regex("""^([A-Z]+)1$""").matchEntire(id)?.let { match ->
            val base = match.groupValues[1]
            if (base != "S" && base != "L") out.add(base) // A1 -> A, TAA1 -> TAA
        }
        SCHEMA_BY_LAW[id]?.let { out.add(it) } // TEE -> S11, TFF -> S15
        if (Regex("""^[A-Z]+$""").matches(id) &&
            id !in NOT_A_LAW_SLOT &&
            id !in SCHEMA_BY_LAW &&
            id !in LAW_BY_SCHEMA
        ) {
            out.add("${id}1") // A -> A1, TAA -> TAA1 (but never TEE1 / S1 / NIL1)
        }
        return out
    }

    /**
     * Every (day, clock) period a token names, scanning theory **and** lab.
     * Day cells are split on `/` and `+`; lunch rows are skipped; invalid clocks
     * drop out rather than becoming midnight.
     */
    fun periodsForSlot(token: String, schema: CampusSchema = CHENNAI): List<SlotPeriod> {
        val wanted = slotSpellings(token)
        if (wanted.isEmpty()) return emptyList()
        val found = mutableListOf<SlotPeriod>()

        fun collect(periods: List<SchemaPeriod>, type: String) {
            periods.forEachIndexed { index, period ->
                if (period.lunch || period.start.isBlank() || period.end.isBlank()) return@forEachIndexed
                val startMin = parseTimeToMinutes(period.start) ?: return@forEachIndexed
                val endMin = parseTimeToMinutes(period.end) ?: return@forEachIndexed
                for ((day, cell) in period.days) {
                    val ids = cell.split(Regex("""[/+]"""))
                        .map { normaliseSlot(it) }
                        .filter { it.isNotEmpty() }
                    if (ids.none { it in wanted }) continue
                    val dayId = day.lowercase()
                    if (dayId !in DAY_IDS) continue
                    found.add(SlotPeriod(dayId, period.start, period.end, startMin, endMin, type, index))
                }
            }
        }

        collect(schema.theory, "theory")
        collect(schema.lab, "lab")
        return found
    }

    /** Same day **and** open-interval intersection. Boundary touching is legal. */
    fun periodsOverlap(a: SlotPeriod, b: SlotPeriod): Boolean =
        a.day == b.day && maxOf(a.startMin, b.startMin) < minOf(a.endMin, b.endMin)

    fun periodListsClash(a: List<SlotPeriod>, b: List<SlotPeriod>): Boolean =
        a.any { x -> b.any { y -> periodsOverlap(x, y) } }

    /** Theory-vs-lab uses the same clock test; no special "lab beats theory" rule. */
    fun slotsOverlap(
        theorySlot: String,
        labSlot: String,
        schema: CampusSchema = CHENNAI,
    ): Boolean {
        val theoryPeriods = splitSlots(theorySlot).flatMap { periodsForSlot(it, schema) }
        val labPeriods = splitSlots(labSlot).flatMap { periodsForSlot(it, schema) }
        return periodListsClash(theoryPeriods, labPeriods)
    }

    /** All lab token ids present in a schema (useful for provenance assertions). */
    fun labTokens(schema: CampusSchema = CHENNAI): Set<String> =
        schema.lab.flatMap { it.days.values }.toSet()

    /** All theory token ids present in a schema. */
    fun theoryTokens(schema: CampusSchema = CHENNAI): Set<String> =
        schema.theory.flatMap { it.days.values }.toSet()
}

// ---------------------------------------------------------------------------
// Generation: turn SlotMap's raw day/token/clock table into the schema shape.
// ---------------------------------------------------------------------------

private fun isLabToken(token: String): Boolean =
    token.length > 1 && token.startsWith("L") && token.drop(1).all { it.isDigit() }

private fun buildChennaiSchema(): CampusSchema {
    val theoryRanges = LinkedHashMap<Pair<Int, Int>, MutableMap<String, String>>()
    val labRanges = LinkedHashMap<Pair<Int, Int>, MutableMap<String, String>>()

    for ((day, slots) in SlotMap.map) {
        val dayId = day.lowercase()
        for ((token, rawClock) in slots) {
            val (start, end) = SlotMap.slotRange(rawClock)
            if (start == 0 && end == 0) continue
            val bucket = if (isLabToken(token)) labRanges else theoryRanges
            bucket.getOrPut(start to end) { LinkedHashMap() }[dayId] = token
        }
    }

    fun toPeriods(ranges: Map<Pair<Int, Int>, Map<String, String>>): List<SchemaPeriod> =
        ranges.entries
            .sortedWith(compareBy<Map.Entry<Pair<Int, Int>, Map<String, String>>> { it.key.first }
                .thenBy { it.key.second })
            .map { (range, days) ->
                SchemaPeriod(
                    start = SlotMap.minutesToTimeStr(range.first),
                    end = SlotMap.minutesToTimeStr(range.second),
                    days = days.toMap(),
                )
            }

    return CampusSchema(
        theory = withLunch(toPeriods(theoryRanges), "1:25 PM"),
        lab = withLunch(toPeriods(labRanges), "1:20 PM"),
    )
}

/** Inserts the lunch spacer between the morning and afternoon blocks. */
private fun withLunch(periods: List<SchemaPeriod>, lunchStart: String): List<SchemaPeriod> {
    val afternoon = periods.indexOfFirst {
        (parseTimeToMinutes(it.start) ?: return@indexOfFirst false) >= FfcsSlotSchema.MORNING_BEFORE_MIN
    }
    val lunch = SchemaPeriod(lunch = true, start = lunchStart, end = "2:00 PM")
    return if (afternoon >= 0) {
        periods.take(afternoon) + lunch + periods.drop(afternoon)
    } else {
        periods + lunch
    }
}
