package com.amazecc.app.shared.ffcs

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
}
