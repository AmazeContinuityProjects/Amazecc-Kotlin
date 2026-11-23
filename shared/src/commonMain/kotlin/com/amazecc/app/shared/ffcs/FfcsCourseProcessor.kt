package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.ffcs.planner.ParsedCourse as CanonicalCourse

object FfcsCourseProcessor {

    fun parseFFCSCSV(csvText: String): List<ParsedCourse> {
        val lines = csvText.trim().replace("\r", "").split("\n")
        val courses = mutableListOf<ParsedCourse>()
        for (line in lines) {
            if (line.isBlank()) continue
            var inQuotes = false
            val cols = mutableListOf<String>()
            val current = StringBuilder()
            for (char in line) {
                if (char == '\"') {
                    inQuotes = !inQuotes
                } else if (char == ',' && !inQuotes) {
                    cols.add(current.toString().trim())
                    current.clear()
                } else {
                    current.append(char)
                }
            }
            cols.add(current.toString().trim())

            if (cols.size >= 7 && !cols[0].equals("CODE", ignoreCase = true)) {
                courses.add(
                    ParsedCourse(
                        code = cols[0],
                        title = cols[1],
                        type = cols[2],
                        credits = cols[3],
                        slot = cols[4],
                        faculty = cols[5],
                        room = cols[6]
                    )
                )
            }
        }
        return processCourses(courses)
    }

    fun processCourses(rawCourses: List<ParsedCourse>): List<ParsedCourse> {
        // 1. Identify mergeable base codes (courses that have both L and P suffixes, e.g. CSE1001L and CSE1001P)
        val hasL = mutableSetOf<String>()
        val hasP = mutableSetOf<String>()

        rawCourses.forEach { c ->
            val code = c.code.trim().uppercase()
            if (code.endsWith("L")) hasL.add(code.dropLast(1))
            else if (code.endsWith("P")) hasP.add(code.dropLast(1))
        }

        val mergeableBases = hasL.intersect(hasP)

        // Map courses to their base code if mergeable
        val mappedParsed = rawCourses.map { c ->
            val code = c.code.trim().uppercase()
            val base = if (code.endsWith("L") || code.endsWith("P")) code.dropLast(1) else code
            if (mergeableBases.contains(base)) {
                c.copy(code = base)
            } else {
                c
            }
        }

        val combined = mutableListOf<ParsedCourse>()
        val byCode = mappedParsed.groupBy { it.code }

        byCode.forEach { (codeKey, coursesList) ->
            val isMergedLPBase = mergeableBases.contains(codeKey)
            val hasEmbedded = isMergedLPBase || coursesList.any { 
                val t = it.type.trim().uppercase()
                t == "ETH" || t == "ELA" || t == "EPJ" || t.contains("EMBEDDED")
            }

            if (hasEmbedded) {
                // Group by faculty for embedded merging
                val byFac = coursesList.groupBy { it.faculty }

                byFac.forEach { (fac, facCourses) ->
                    val theorySlots = facCourses.filter {
                        val t = it.type.trim().uppercase()
                        t == "ETH" || t == "TH" || it.code.endsWith("L") || (!it.slot.startsWith("L") && it.slot != "NIL")
                    }.toMutableList()

                    val labSlots = facCourses.filter {
                        val t = it.type.trim().uppercase()
                        t == "ELA" || t == "LO" || it.code.endsWith("P") || it.slot.startsWith("L")
                    }.toMutableList()

                    if (theorySlots.isNotEmpty() && labSlots.isNotEmpty()) {
                        // For simplicity in this port, we pair the first available theory with the first available lab 
                        // from the same faculty (VIT guarantees 1:1 if faculty is same)
                        while (theorySlots.isNotEmpty() && labSlots.isNotEmpty()) {
                            val t = theorySlots.removeAt(0)
                            val l = labSlots.removeAt(0)
                            
                            val tType = t.type.trim().uppercase()
                            val lType = l.type.trim().uppercase()
                            
                            val combinedType = "$tType+$lType"
                            val combinedTitle = "${t.title} [Embedded Theory and Lab]"
                            val combinedCredits = ((t.credits.toDoubleOrNull() ?: 0.0) + (l.credits.toDoubleOrNull() ?: 0.0)).toString()
                            
                            combined.add(
                                ParsedCourse(
                                    code = codeKey,
                                    title = combinedTitle,
                                    type = combinedType,
                                    credits = combinedCredits,
                                    room = "${t.room} / ${l.room}",
                                    slot = "${t.slot}+${l.slot}",
                                    faculty = fac
                                )
                            )
                        }
                    }
                    
                    // Add any leftovers
                    combined.addAll(theorySlots)
                    combined.addAll(labSlots)
                }
            } else {
                combined.addAll(coursesList)
            }
        }

        return combined
    }

    // ── D-176 canonical APIs ───────────────────────────────────────────────
    // These return the D-171 `ffcs.planner.ParsedCourse` (UPPERCASE fields).
    // The legacy functions above keep their lowercase model and stay untouched
    // because the current screens still call them.

    // Lab windows are derived once from SlotMap.labTokensOf (the generated
    // SLOTS_BY_PERIOD equivalent), not hand-maintained loan arrays.
    private val coreMorningLabs: Set<String> by lazy { SlotMap.coreMorningLabSlots() }
    private val coreEveningLabs: Set<String> by lazy { SlotMap.coreEveningLabSlots() }
    private val lateMorningLabs: Set<String> by lazy { SlotMap.lateMorningLabSlots() }
    private val lateEveningLabs: Set<String> by lazy { SlotMap.lateEveningLabSlots() }

    /** A manual cross-faculty link, port of the web `ManualLink` shape. */
    data class ManualLink(
        val CODE: String,
        val TYPE: String,
        val SLOT: String,
        val FACULTY: String,
        val LINK_ID: String,
    )

    /** Parse any accepted CSV dialect into canonical D-171 rows. */
    fun parseCanonicalCsv(csvText: String): List<CanonicalCourse> =
        FfcsCsv.parseReportRows(csvText)

    /**
     * Four-tier theory/lab pairing score from `ffcsCourseGrouping.ts:47-108`
     * and reference/05 §3.1. Lab window sets come from `SlotMap.labTokensOf`
     * (the generated `SLOTS_BY_PERIOD` equivalent), never a literal token list.
     */
    fun computePairScore(
        theory: CanonicalCourse,
        lab: CanonicalCourse,
        schema: CampusSchema = FfcsSlotSchema.CHENNAI,
    ): Int {
        val morningBeforeMin = FfcsSlotSchema.MORNING_BEFORE_MIN // 840 (2:00 PM)

        fun periodsOf(slot: String) = FfcsSlotSchema.splitSlots(slot)
            .filter { it != "NIL" }
            .flatMap { FfcsSlotSchema.periodsForSlot(it, schema) }

        val theoryPeriods = periodsOf(theory.SLOT)
        val labPeriods = periodsOf(lab.SLOT)

        val theoryMorning = theoryPeriods.any { it.startMin < morningBeforeMin }
        val theoryEvening = theoryPeriods.any { it.startMin >= morningBeforeMin }

        val labStarts = labPeriods.filter { it.type == "lab" }.map { it.startMin }
        val hasMorningLab = labStarts.any { it < morningBeforeMin }
        val hasEveningLab = labStarts.any { it >= morningBeforeMin }
        val morningLabOnly = labStarts.isNotEmpty() && labStarts.all { it < morningBeforeMin }
        val eveningLabOnly = labStarts.isNotEmpty() && labStarts.all { it >= morningBeforeMin }

        val labTokens = FfcsSlotSchema.splitSlots(lab.SLOT).filter { it.isNotEmpty() }
        fun allTokensIn(window: Set<String>): Boolean =
            labTokens.isNotEmpty() && labTokens.all { it in window }

        val coreMorning = allTokensIn(coreMorningLabs)
        val coreEvening = allTokensIn(coreEveningLabs)
        val lateMorning = allTokensIn(lateMorningLabs)
        val lateEvening = allTokensIn(lateEveningLabs)

        var score = 0
        // Priority 1: complementary core windows.
        if ((theoryEvening && coreMorning) || (theoryMorning && coreEvening)) {
            score += 2000
        }
        // Priority 2: permitted late windows.
        else if ((theoryMorning && lateMorning) || (theoryEvening && lateEvening)) {
            score += 1000
        }
        // Priority 3: general cross-shift / mixed halves.
        else if ((theoryEvening && morningLabOnly) || (theoryMorning && eveningLabOnly)) {
            score += 500
        } else if (hasMorningLab && hasEveningLab) {
            score += 200
        }
        // Priority 4: bad same-shift pairing.
        else {
            score -= 1000
        }

        // Same day AND same half-day: -30 per overlapping period pair.
        for (tp in theoryPeriods) {
            for (lp in labPeriods) {
                if (tp.day == lp.day && (tp.startMin < morningBeforeMin) == (lp.startMin < morningBeforeMin)) {
                    score -= 30
                }
            }
        }

        // Sections registered together: +20 within 2, +10 within 10.
        score += FfcsCsv.classIdProximityBonus(theory, lab)
        return score
    }

    private fun formatCredits(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun createCombinedCourse(theory: CanonicalCourse, lab: CanonicalCourse): CanonicalCourse {
        val theoryType = theory.TYPE.trim().uppercase()
        val labType = lab.TYPE.trim().uppercase()
        val combinedType = if (theoryType.isNotEmpty() && labType.isNotEmpty()) {
            "$theoryType+$labType"
        } else {
            "ETH+ELA"
        }

        val combinedTitle = if (theory.ORIGINAL_CODE?.endsWith("L") == true &&
            lab.ORIGINAL_CODE?.endsWith("P") == true
        ) {
            val suffix = if (theoryType == "ETH" || "EMBEDDED" in theoryType ||
                labType == "ELA" || "EMBEDDED" in labType
            ) "Embedded Theory and Lab" else "Theory + Lab"
            "${theory.TITLE} [$suffix]"
        } else {
            "${theory.TITLE} [Embedded Theory and Lab]"
        }

        val combinedFaculty = if (theory.FACULTY.isNotEmpty() && lab.FACULTY.isNotEmpty() &&
            theory.FACULTY != lab.FACULTY
        ) "${theory.FACULTY} / ${lab.FACULTY}" else theory.FACULTY

        val credits = if ("EMBEDDED" in theoryType && "EMBEDDED" in labType) {
            maxOf(theory.CREDITS.toDoubleOrNull() ?: 0.0, lab.CREDITS.toDoubleOrNull() ?: 0.0)
        } else {
            (theory.CREDITS.toDoubleOrNull() ?: 0.0) + (lab.CREDITS.toDoubleOrNull() ?: 0.0)
        }

        return theory.copy(
            TYPE = combinedType,
            TITLE = combinedTitle,
            CREDITS = formatCredits(credits),
            SLOT = "${theory.SLOT}+${lab.SLOT}",
            CLASS_IDS = listOfNotNull(theory.CLASS_ID, lab.CLASS_ID).distinct(),
            ROOM = if (theory.ROOM.isNotEmpty() && lab.ROOM.isNotEmpty()) {
                "${theory.ROOM} / ${lab.ROOM}"
            } else theory.ROOM.ifEmpty { lab.ROOM },
            ORIGINAL_CODE = theory.ORIGINAL_CODE ?: theory.CODE,
            FACULTY = combinedFaculty,
        )
    }

    private fun isTheoryHalf(course: CanonicalCourse): Boolean {
        val type = course.TYPE.trim().uppercase()
        val original = course.ORIGINAL_CODE ?: course.CODE
        return type == "ETH" || type == "TH" || original.endsWith("L") ||
            (!course.SLOT.startsWith("L") && course.SLOT != "NIL")
    }

    private fun isLabHalf(course: CanonicalCourse): Boolean {
        val type = course.TYPE.trim().uppercase()
        val original = course.ORIGINAL_CODE ?: course.CODE
        return type == "ELA" || type == "LO" || original.endsWith("P") || course.SLOT.startsWith("L")
    }

    /**
     * Canonical replacement for the naive first-to-first pairing above — the
     * web `processParsedCourses` (`ffcsCourseGrouping.ts:147-385`). Grouping is
     * by CODE, then by ERP id (falling back to faculty name), with a scored
     * branch-and-bound maximum matching. Every chosen pair is clash-free and
     * maximises [computePairScore]; the inline 20000-step guard keeps a large
     * batch from exploring forever.
     */
    fun processCanonicalCourses(
        parsed: List<CanonicalCourse>,
        manualLinks: List<ManualLink> = emptyList(),
        schema: CampusSchema = FfcsSlotSchema.CHENNAI,
    ): List<CanonicalCourse> {
        // 1. L/P variant codes merge to a shared base code.
        val hasL = mutableSetOf<String>()
        val hasP = mutableSetOf<String>()
        parsed.forEach { course ->
            val code = course.CODE.trim().uppercase()
            if (code.endsWith("L")) hasL.add(code.dropLast(1))
            else if (code.endsWith("P")) hasP.add(code.dropLast(1))
        }
        val mergeableBases = hasL intersect hasP

        val mapped = parsed.map { course ->
            val code = course.CODE.trim().uppercase()
            val base = if (code.endsWith("L") || code.endsWith("P")) code.dropLast(1) else code
            var result = course
            if (base in mergeableBases) {
                result = result.copy(ORIGINAL_CODE = course.CODE, CODE = base)
            }
            val link = manualLinks.firstOrNull {
                it.CODE == course.CODE && it.TYPE == course.TYPE &&
                    it.SLOT == course.SLOT && it.FACULTY == course.FACULTY
            }
            if (link != null) result = result.copy(LINK_ID = link.LINK_ID)
            result
        }

        val combined = mutableListOf<CanonicalCourse>()
        val byCode = LinkedHashMap<String, MutableList<CanonicalCourse>>()
        mapped.forEach { byCode.getOrPut(it.CODE) { mutableListOf() }.add(it) }

        byCode.forEach { (codeKey, coursesList) ->
            val isMergedLpBase = codeKey in mergeableBases
            // A blank TYPE is embedded too: the winter report has no TYPE column.
            val hasEmbedded = isMergedLpBase || coursesList.any { course ->
                val type = course.TYPE.trim().uppercase()
                type.isEmpty() || type == "ETH" || type == "ELA" || type == "EPJ" ||
                    "EMBEDDED" in type
            }
            if (!hasEmbedded) {
                combined.addAll(coursesList)
                return@forEach
            }

            val consumed = mutableSetOf<Int>()

            // 1. Explicit manual cross-faculty links first.
            val manualTheories = coursesList.mapIndexedNotNull { index, course ->
                if (course.LINK_ID != null && isTheoryHalf(course)) index to course else null
            }
            val manualLabs = coursesList.mapIndexedNotNull { index, course ->
                if (course.LINK_ID != null && isLabHalf(course)) index to course else null
            }
            val linkIds = (manualTheories + manualLabs).mapNotNull { it.second.LINK_ID }.toSet()
            linkIds.forEach { id ->
                val theories = manualTheories.filter { it.second.LINK_ID == id }
                val labs = manualLabs.filter { it.second.LINK_ID == id }
                if (theories.isNotEmpty() && labs.isNotEmpty()) {
                    theories.forEach { theory ->
                        labs.forEach { lab -> combined.add(createCombinedCourse(theory.second, lab.second)) }
                    }
                    theories.forEach { consumed.add(it.first) }
                    labs.forEach { consumed.add(it.first) }
                }
            }

            // 2. Normal processing for the rest, keyed by ERP id when present.
            val remaining = coursesList.mapIndexedNotNull { index, course ->
                if (index !in consumed) index to course else null
            }
            val byFaculty = LinkedHashMap<String, MutableList<Pair<Int, CanonicalCourse>>>()
            remaining.forEach { (index, course) ->
                byFaculty.getOrPut(course.ERP_ID ?: course.FACULTY) { mutableListOf() }.add(index to course)
            }

            byFaculty.forEach { (_, facultyPairs) ->
                val facultyCourses = facultyPairs.map { it.second }
                val theorySlots = facultyPairs.filter { isTheoryHalf(it.second) }.toMutableList()
                val labSlots = facultyPairs.filter { isLabHalf(it.second) }.toMutableList()

                if (theorySlots.isEmpty() || labSlots.isEmpty()) {
                    combined.addAll(facultyCourses)
                    return@forEach
                }

                // Priority 5: one theory with many non-overlapping labs fans out.
                if (theorySlots.size == 1 && labSlots.size > 1) {
                    val theory = theorySlots[0].second
                    val nonOverlapping = labSlots.filter {
                        !FfcsSlotSchema.slotsOverlap(theory.SLOT, it.second.SLOT, schema)
                    }
                    if (nonOverlapping.isNotEmpty()) {
                        nonOverlapping.forEach { combined.add(createCombinedCourse(theory, it.second)) }
                        labSlots.filter {
                            FfcsSlotSchema.slotsOverlap(theory.SLOT, it.second.SLOT, schema)
                        }.forEach { combined.add(it.second) }
                        return@forEach
                    }
                }

                var bestMatch: List<Pair<Int, Int>> = emptyList()
                var bestScore = Int.MIN_VALUE
                val maxPossible = minOf(theorySlots.size, labSlots.size)
                var steps = 0

                fun backtrack(
                    theoryIndex: Int,
                    currentMatch: MutableList<Pair<Int, Int>>,
                    usedLabs: MutableSet<Int>,
                    currentScore: Int,
                ) {
                    if (currentMatch.size > bestMatch.size ||
                        (currentMatch.size == bestMatch.size && currentScore > bestScore)
                    ) {
                        bestMatch = currentMatch.toList()
                        bestScore = currentScore
                    }
                    if (theoryIndex >= theorySlots.size) return
                    if (currentMatch.size + (theorySlots.size - theoryIndex) < bestMatch.size) return
                    val maxRemaining = (maxPossible - currentMatch.size) * 2100
                    if (bestMatch.size == maxPossible && currentScore + maxRemaining <= bestScore) return
                    // Inline branch-and-bound guard deliberately has no named constant
                    // (skill audit: reference/05 §3.2, ffcsCourseGrouping.ts:335).
                    if (++steps > 20000) return

                    val theory = theorySlots[theoryIndex].second
                    val candidates = labSlots.mapIndexed { labIndex, pair ->
                        Triple(labIndex, pair.second, computePairScore(theory, pair.second, schema))
                    }
                        .filter { (labIndex, lab, _) ->
                            labIndex !in usedLabs &&
                                !FfcsSlotSchema.slotsOverlap(theory.SLOT, lab.SLOT, schema)
                        }
                        .sortedByDescending { it.third } // stable sort

                    for ((labIndex, _, score) in candidates) {
                        usedLabs.add(labIndex)
                        currentMatch.add(theoryIndex to labIndex)
                        backtrack(theoryIndex + 1, currentMatch, usedLabs, currentScore + score)
                        currentMatch.removeAt(currentMatch.lastIndex)
                        usedLabs.remove(labIndex)
                    }
                    backtrack(theoryIndex + 1, currentMatch, usedLabs, currentScore)
                }

                backtrack(0, mutableListOf(), mutableSetOf(), 0)

                if (bestMatch.isNotEmpty()) {
                    val matchedTheories = bestMatch.map { it.first }.toSet()
                    val matchedLabs = bestMatch.map { it.second }.toSet()
                    bestMatch.forEach { (theoryIndex, labIndex) ->
                        combined.add(createCombinedCourse(theorySlots[theoryIndex].second, labSlots[labIndex].second))
                    }
                    theorySlots.forEachIndexed { index, pair ->
                        if (index !in matchedTheories) combined.add(pair.second)
                    }
                    labSlots.forEachIndexed { index, pair ->
                        if (index !in matchedLabs) combined.add(pair.second)
                    }
                } else {
                    combined.addAll(facultyCourses)
                }
            }
        }

        return combined
    }

    /** Convenience seam: parse a user-supplied CSV and pair it canonically. */
    fun pairCanonicalCsv(csvText: String): List<CanonicalCourse> =
        processCanonicalCourses(parseCanonicalCsv(csvText))

    /** Alias used by newer call sites that prefer the explicit verb. */
    fun pairTheoryAndLab(
        parsed: List<CanonicalCourse>,
        manualLinks: List<ManualLink> = emptyList(),
        schema: CampusSchema = FfcsSlotSchema.CHENNAI,
    ): List<CanonicalCourse> = processCanonicalCourses(parsed, manualLinks, schema)

    /** Web-parity alias (`processParsedCourses`) for the canonical groupings. */
    fun processParsedCourses(
        parsed: List<CanonicalCourse>,
        manualLinks: List<ManualLink> = emptyList(),
    ): List<CanonicalCourse> = processCanonicalCourses(parsed, manualLinks)
}
