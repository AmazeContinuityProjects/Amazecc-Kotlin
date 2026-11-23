package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.DayId
import kotlinx.coroutines.yield

/**
 * D-180 — the backtracking walk, ported from `ffcsWalk.ts` (reference/04 §3).
 *
 * Pure and visitor-style: the caller decides what to do with each surviving
 * week. The three monotone rules (separate lab days, no consecutive labs,
 * half-day floor) are precomputed into bitmasks so a partial week that has
 * already broken one is cut without descending. Cutting at the first break is
 * safe because every rule is monotone: adding more courses cannot repair it.
 *
 * Cancellation is cooperative: every [TICK_INTERVAL] nodes the walk yields to
 * the coroutine dispatcher and asks the hook whether to stop. There is no RNG
 * and no state outside the arguments — visit order is report order + DFS.
 */

/** Days the walk reasons about — the same five the web `DAYS_OF_WEEK` uses. */
private val WALK_DAYS: List<String> = DayId.DAYS_OF_WEEK.map { it.value }

private fun dayIndex(day: String): Int = WALK_DAYS.indexOf(day.lowercase())

private fun popcount(value: Int): Int {
    var bits = 0
    var rest = value
    while (rest != 0) {
        rest = rest and (rest - 1)
        bits++
    }
    return bits
}

/** Does this week put two different courses' lab bookings on one day? */
fun labsShareADay(
    combination: List<ParsedCourse>,
    labPeriodsByOffering: Map<ParsedCourse, List<SlotPeriod>>,
): Boolean {
    val taken = HashSet<String>()
    for (offering in combination) {
        val labPeriods = labPeriodsByOffering[offering] ?: continue
        for (day in labPeriods.map { it.day }.toSet()) {
            if (!taken.add(day)) return true
        }
    }
    return false
}

private fun labPeriodPairKey(day: String, firstIndex: Int, secondIndex: Int): String =
    "$day:${minOf(firstIndex, secondIndex)}:${maxOf(firstIndex, secondIndex)}"

/** Every pair of lab periods on one day that count as consecutive. */
fun consecutiveLabPeriodPairs(
    schema: CampusSchema,
    lunchBreakSeparatesLabs: Boolean = false,
): Set<String> {
    val pairs = LinkedHashSet<String>()
    for (day in WALK_DAYS) {
        val dayPeriods = schema.lab
            .mapIndexedNotNull { index, period ->
                if (period.lunch || period.start.isBlank() || period.end.isBlank()) return@mapIndexedNotNull null
                if (!period.days.containsKey(day)) return@mapIndexedNotNull null
                val startMin = parseTimeToMinutes(period.start) ?: return@mapIndexedNotNull null
                index to startMin
            }
            .sortedWith(compareBy({ it.second }, { it.first }))
        for (i in 0 until dayPeriods.size - 1) {
            val first = dayPeriods[i]
            val second = dayPeriods[i + 1]
            if (lunchBreakSeparatesLabs &&
                schema.lab.slice(first.first + 1 until second.first).any { it.lunch }
            ) continue
            pairs.add(labPeriodPairKey(day, first.first, second.first))
        }
    }
    return pairs
}

/** Do two different courses occupy consecutive lab periods on one day? */
fun labsAreConsecutive(
    combination: List<ParsedCourse>,
    labPeriodsByOffering: Map<ParsedCourse, List<SlotPeriod>>,
    consecutivePairs: Set<String>,
): Boolean {
    for (firstIndex in combination.indices) {
        val firstPeriods = labPeriodsByOffering[combination[firstIndex]] ?: continue
        for (secondIndex in firstIndex + 1 until combination.size) {
            val secondPeriods = labPeriodsByOffering[combination[secondIndex]] ?: continue
            if (firstPeriods.any { first ->
                    secondPeriods.any { second ->
                        first.day == second.day &&
                            consecutivePairs.contains(labPeriodPairKey(first.day, first.index, second.index))
                    }
                }
            ) return true
        }
    }
    return false
}

/** One ten-cell half-day slot set, as built by the web `halfDayCells`. */
private data class HalfDayCell(val bit: Int, val slots: Set<String>)

private fun halfDayCells(schema: CampusSchema): List<HalfDayCell> {
    val theory = schema.theory.filterNot { it.lunch }
    val lab = schema.lab.filterNot { it.lunch }
    val cells = ArrayList<HalfDayCell>()
    theory.forEachIndexed { index, period ->
        if (period.start.isBlank() || period.end.isBlank()) return@forEachIndexed
        val startMin = parseTimeToMinutes(period.start) ?: return@forEachIndexed
        val endMin = parseTimeToMinutes(period.end) ?: return@forEachIndexed
        val evening = if (startMin >= FfcsSlotSchema.MORNING_BEFORE_MIN) 1 else 0
        WALK_DAYS.forEachIndexed { dayIndex, day ->
            val slots = LinkedHashSet<String>()
            period.days[day]?.let { slots.add(it) }
            lab.getOrNull(index)?.days?.get(day)?.let { slots.add(it) }
            if (slots.isNotEmpty()) cells.add(HalfDayCell(dayIndex * 2 + evening, slots))
        }
    }
    return cells
}

/** One lab period's own bit and the bits of its consecutive neighbours. */
data class LabSite(val day: Int, val own: Int, val threat: Int)

/** Per-offering bit facts, precomputed once per run. */
data class WalkRules(
    val minHalfDays: Int = 0,
    val labDayMasks: List<List<Int>>? = null,
    val labSites: List<List<List<LabSite>>>? = null,
    val halfDayMasks: List<List<Int>>? = null,
)

/**
 * Build only the masks this run asked for; `null` means the plain clash-only
 * walk. The visit-time checks in the caller remain as a belt-and-braces
 * fallback — for a run with no rules they are the whole of each rule.
 */
fun buildWalkRules(
    minHalfDays: Int,
    separateLabDays: Boolean,
    avoidConsecutiveLabs: Boolean,
    schema: CampusSchema,
    optionsPerCourse: List<List<ParsedCourse>>,
    labPeriodsByOffering: Map<ParsedCourse, List<SlotPeriod>>,
    consecutiveLabPairs: Set<String>,
): WalkRules? {
    val floor = if (minHalfDays > 0) minHalfDays else 0
    var labDayMasks: List<List<Int>>? = null
    var labSites: List<List<List<LabSite>>>? = null
    var halfDayMasks: List<List<Int>>? = null

    if (separateLabDays) {
        labDayMasks = optionsPerCourse.map { options ->
            options.map { offering ->
                var mask = 0
                for (period in labPeriodsByOffering[offering].orEmpty()) {
                    val index = dayIndex(period.day)
                    if (index < 0) continue // weekend labs are outside the five-day mask
                    mask = mask or (1 shl index)
                }
                mask
            }
        }
    }

    if (avoidConsecutiveLabs && schema.lab.size <= 31) {
        val neighbours = HashMap<String, MutableList<Int>>()
        for (key in consecutiveLabPairs) {
            val parts = key.split(":")
            if (parts.size != 3) continue
            val day = parts[0]
            val first = parts[1].toIntOrNull() ?: continue
            val second = parts[2].toIntOrNull() ?: continue
            for ((self, other) in listOf(first to second, second to first)) {
                neighbours.getOrPut("$day:$self") { ArrayList() }.add(other)
            }
        }
        labSites = optionsPerCourse.map { options ->
            options.map { offering ->
                val sites = ArrayList<LabSite>()
                for (period in labPeriodsByOffering[offering].orEmpty()) {
                    val day = dayIndex(period.day)
                    if (day < 0) continue // weekend labs are outside the five-day mask
                    var threat = 0
                    for (other in neighbours["${period.day}:${period.index}"].orEmpty()) {
                        threat = threat or (1 shl other)
                    }
                    sites.add(LabSite(day, 1 shl period.index, threat))
                }
                sites
            }
        }
    }

    if (floor > 0) {
        val cells = halfDayCells(schema)
        halfDayMasks = optionsPerCourse.map { options ->
            options.map { offering ->
                val owned = FfcsSlotSchema.splitSlots(offering.slot)
                    .flatMap { FfcsSlotSchema.slotSpellings(it) }
                    .toSet()
                var mask = 0
                for (cell in cells) {
                    if (cell.slots.any { it in owned }) mask = mask or (1 shl cell.bit)
                }
                mask
            }
        }
    }

    return if (labDayMasks != null || labSites != null || halfDayMasks != null) {
        WalkRules(floor, labDayMasks, labSites, halfDayMasks)
    } else {
        null
    }
}

/** What the caller observes at each surviving week. */
fun interface WalkHooks {
    fun visit(combination: List<ParsedCourse>)
}

/** Outcome of one walk, including which rules actually cut branches. */
data class WalkOutcome(
    val found: Int,
    val cancelled: Boolean = false,
    val labDayPruned: Boolean = false,
    val consecutivePruned: Boolean = false,
    val halfDaysPruned: Boolean = false,
)

/**
 * Every conflict-free combination of one offering per course, in report order,
 * stopping at `limit`. `windows` is precomputed once per offering, so the
 * clash test is a lookup rather than a schema walk on every node.
 *
 * `tick` is called every [TICK_INTERVAL] nodes with `(nodes, found)`; return
 * `true` (or flip [shouldStop]) to cancel cooperatively. Cancelling does not
 * throw — the caller receives the partial [WalkOutcome].
 */
suspend fun walkCombinations(
    options: List<List<ParsedCourse>>,
    windows: List<List<List<SlotPeriod>>>,
    limit: Long,
    hooks: WalkHooks,
    rules: WalkRules? = null,
    tick: ((nodes: Int, found: Int) -> Unit)? = null,
    shouldStop: () -> Boolean = { false },
): WalkOutcome {
    val chosen = ArrayList<ParsedCourse>()
    val chosenWindows = ArrayList<SlotPeriod>()
    var found = 0
    var nodes = 0
    var cancelled = false
    var halted = false
    var labDayPruned = false
    var consecutivePruned = false
    var halfDaysPruned = false

    var claimedLabDays = 0
    val claimedLabSites = IntArray(WALK_DAYS.size)
    var claimedHalfDays = 0
    val halfDayHistory = ArrayList<Int>()
    val minHalfDays = rules?.minHalfDays ?: 0

    suspend fun walk(index: Int) {
        if (halted) return
        if (found.toLong() >= limit) {
            halted = true
            return
        }
        if (index == options.size) {
            found++
            hooks.visit(chosen.toList())
            return
        }
        if (++nodes % TICK_INTERVAL == 0) {
            yield()
            tick?.invoke(nodes, found)
            if (shouldStop()) {
                cancelled = true
                halted = true
                return
            }
        }
        val offeringWindows = windows[index]
        val labDayMasks = rules?.labDayMasks?.getOrNull(index)
        val labSites = rules?.labSites?.getOrNull(index)
        val halfDayMasks = rules?.halfDayMasks?.getOrNull(index)
        for (i in options[index].indices) {
            val periods = offeringWindows[i]
            if (FfcsSlotSchema.periodListsClash(periods, chosenWindows)) continue
            if (labDayMasks != null && claimedLabDays and labDayMasks[i] != 0) {
                labDayPruned = true
                continue
            }
            if (labSites != null) {
                val sites = labSites[i]
                var consecutiveHit = false
                for (site in sites) {
                    if (claimedLabSites[site.day] and site.threat != 0) {
                        consecutiveHit = true
                        break
                    }
                }
                if (consecutiveHit) {
                    consecutivePruned = true
                    continue
                }
            }
            if (halfDayMasks != null && HALF_DAY_CELLS - popcount(claimedHalfDays or halfDayMasks[i]) < minHalfDays) {
                halfDaysPruned = true
                continue
            }

            chosen.add(options[index][i])
            chosenWindows.addAll(periods)
            if (labDayMasks != null) claimedLabDays = claimedLabDays or labDayMasks[i]
            if (labSites != null) {
                for (site in labSites[i]) claimedLabSites[site.day] = claimedLabSites[site.day] or site.own
            }
            if (halfDayMasks != null) {
                halfDayHistory.add(claimedHalfDays)
                claimedHalfDays = claimedHalfDays or halfDayMasks[i]
            }

            walk(index + 1)

            if (halfDayMasks != null) claimedHalfDays = halfDayHistory.removeAt(halfDayHistory.lastIndex)
            if (labSites != null) {
                for (site in labSites[i]) claimedLabSites[site.day] = claimedLabSites[site.day] and site.own.inv()
            }
            if (labDayMasks != null) claimedLabDays = claimedLabDays and labDayMasks[i].inv()
            chosenWindows.subList(chosenWindows.size - periods.size, chosenWindows.size).clear()
            chosen.removeAt(chosen.lastIndex)
            if (halted) return
        }
    }

    walk(0)
    return WalkOutcome(found, cancelled, labDayPruned, consecutivePruned, halfDaysPruned)
}
