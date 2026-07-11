package com.amazecc.app.shared.utils

data class Period(val day: String, val startMin: Int, val endMin: Int)

data class CourseOption(
    val code: String,
    val title: String,
    val faculty: String,
    val room: String,
    val slot: String,
    val credits: Int,
    val type: String,
    val periods: List<Period>
)

data class GeneratedTimetable(
    val id: String,
    val courses: List<CourseOption>,
    val socialScore: Int,
    val halfDays: Int,
    val gaps: Int
)

object FfcsPlannerAlgorithm {

    fun generateTimetables(
        targetCodes: List<String>,
        optionsPerCourse: List<List<CourseOption>>,
        maxResults: Int = 50
    ): List<GeneratedTimetable> {
        val results = mutableListOf<List<CourseOption>>()

        fun backtrack(
            courseIndex: Int,
            currentCombo: MutableList<CourseOption>,
            currentPeriods: MutableList<Period>
        ) {
            if (results.size >= maxResults) return
            if (courseIndex == targetCodes.size) {
                results.add(currentCombo.toList())
                return
            }

            if (courseIndex >= optionsPerCourse.size) return
            
            val options = optionsPerCourse[courseIndex]
            for (opt in options) {
                var hasConflict = false
                for (np in opt.periods) {
                    for (ep in currentPeriods) {
                        if (np.day == ep.day && kotlin.math.max(np.startMin, ep.startMin) < kotlin.math.min(np.endMin, ep.endMin)) {
                            hasConflict = true
                            break
                        }
                    }
                    if (hasConflict) break
                }

                if (!hasConflict) {
                    currentCombo.add(opt)
                    val newPeriods = mutableListOf<Period>().apply {
                        addAll(currentPeriods)
                        addAll(opt.periods)
                    }
                    backtrack(courseIndex + 1, currentCombo, newPeriods)
                    currentCombo.removeAt(currentCombo.size - 1)
                }
            }
        }

        backtrack(0, mutableListOf(), mutableListOf())

        return results.mapIndexed { index, combo ->
            GeneratedTimetable(
                id = "TT-${index}",
                courses = combo,
                socialScore = 0, // Mock metrics for now
                halfDays = 0,
                gaps = 0
            )
        }
    }
}
