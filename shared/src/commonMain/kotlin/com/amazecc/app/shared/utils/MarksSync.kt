package com.amazecc.app.shared.utils

import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlin.math.roundToInt

expect suspend fun hashStringSha256(str: String): String

// Assuming Settings or similar Key-Value store interface is provided
interface KeyValueStore {
    fun getString(key: String): String?
    fun setString(key: String, value: String)
}

@Suppress("unused")
object MarksSync {
    private const val API_BASE = "https://amazecc.vercel.app" // Adjust accordingly
    
    private fun getNumericValue(value: Any?, fallback: Double = 0.0): Double {
        if (value == null) return fallback
        return value.toString().toDoubleOrNull() ?: fallback
    }

    private fun getAssessmentTotals(assessments: JsonArray?): AssessmentTotals {
        var max = 0.0
        var scored = 0.0
        var weightPercent = 0.0
        var weighted = 0.0

        assessments?.forEach { element ->
            val asm = element.jsonObject
            max += getNumericValue(asm["maxMark"]?.jsonPrimitive?.content)
            scored += getNumericValue(asm["scoredMark"]?.jsonPrimitive?.content)
            weightPercent += getNumericValue(asm["weightagePercent"]?.jsonPrimitive?.content)
            weighted += getNumericValue(asm["weightageMark"]?.jsonPrimitive?.content)
        }
        return AssessmentTotals(max, scored, weightPercent, weighted)
    }

    private data class AssessmentTotals(
        val max: Double,
        val scored: Double,
        val weightPercent: Double,
        val weighted: Double
    )

    private fun getCourseCredits(course: JsonObject?): Double {
        val credits = getNumericValue(course?.get("credits")?.jsonPrimitive?.content, -1.0)
        return if (credits > 0) credits else -1.0
    }

    private fun getCourseStats(group: CourseGroup): Int {
        val theoryTotals = getAssessmentTotals(group.theory?.get("assessments") as? JsonArray)
        val labTotals = getAssessmentTotals(group.lab?.get("assessments") as? JsonArray)

        if (group.lab == null) {
            val projected = if (theoryTotals.weightPercent > 0) {
                ((theoryTotals.weighted / theoryTotals.weightPercent) * 100).roundToInt()
            } else 0
            return projected
        }

        if (group.theory == null) {
            val projected = if (labTotals.weightPercent > 0) {
                ((labTotals.weighted / labTotals.weightPercent) * 100).roundToInt()
            } else 0
            return projected
        }

        val theoryCredits = getCourseCredits(group.theory)
        val labCredits = getCourseCredits(group.lab)

        if (theoryCredits < 0 || labCredits < 0) {
            return 0
        }

        val creditsTotal = theoryCredits + labCredits
        val combinedWeighted = (theoryCredits * theoryTotals.weighted + labCredits * labTotals.weighted) / creditsTotal
        val combinedWeightPercent = (theoryCredits * theoryTotals.weightPercent + labCredits * labTotals.weightPercent) / creditsTotal

        val projected = if (combinedWeightPercent > 0) {
            ((combinedWeighted / combinedWeightPercent) * 100).roundToInt()
        } else 0

        return projected
    }

    private data class CourseGroup(
        val courseCode: String,
        var theory: JsonObject? = null,
        var lab: JsonObject? = null
    )

    @Suppress("unused")
    suspend fun syncMarksDiff(
        oldMarksDataStr: String?,
        newMarksDataStr: String?,
        username: String?,
        store: KeyValueStore,
        client: HttpClient
    ) {
        if (username.isNullOrEmpty() || newMarksDataStr.isNullOrEmpty()) return

        try {
            val hasSyncedBefore = store.getString("hasSyncedMarksV2") != null
            val actualOldMarksStr = if (!hasSyncedBefore) "{}" else (oldMarksDataStr ?: "{}")

            val oldMarksData = try { Json.parseToJsonElement(actualOldMarksStr).jsonObject } catch (_: Exception) { buildJsonObject {} }
            val newMarksData = try { Json.parseToJsonElement(newMarksDataStr).jsonObject } catch (_: Exception) { buildJsonObject {} }

            if (!newMarksData.containsKey("courses")) return

            fun buildMap(marksData: JsonObject): Map<String, CourseGroup> {
                val map = mutableMapOf<String, CourseGroup>()
                val courses = marksData["courses"] as? JsonArray ?: return map
                
                for (element in courses) {
                    val c = element.jsonObject
                    val courseCode = c["courseCode"]?.jsonPrimitive?.content ?: continue
                    val courseType = c["courseType"]?.jsonPrimitive?.content ?: ""
                    val slot = c["slot"]?.jsonPrimitive?.content ?: ""
                    
                    val isLab = courseType.lowercase().contains("lab") || slot.lowercase().startsWith("l")
                    
                    if (!map.containsKey(courseCode)) {
                        map[courseCode] = CourseGroup(
                            courseCode = courseCode,
                            theory = if (!isLab) c else null,
                            lab = if (isLab) c else null
                        )
                    } else {
                        val existing = map[courseCode]!!
                        if (isLab) existing.lab = c
                        else existing.theory = c
                    }
                }
                return map
            }

            val oldMap = buildMap(oldMarksData)
            val newMap = buildMap(newMarksData)
            val actions = mutableListOf<JsonObject>()

            newMap.forEach { (courseCode, newGroup) ->
                val oldGroup = oldMap[courseCode] ?: CourseGroup(courseCode)
                val mainCourse = newGroup.theory ?: newGroup.lab ?: return@forEach
                val classId = mainCourse["classNbr"]?.jsonPrimitive?.content ?: return@forEach

                val oldStatsProjected = if (oldGroup.theory != null || oldGroup.lab != null) {
                    getCourseStats(oldGroup)
                } else null
                
                val newStatsProjected = getCourseStats(newGroup)

                if (newStatsProjected > 0) {
                    if (oldStatsProjected == null || oldStatsProjected == 0) {
                        actions.add(buildJsonObject {
                            put("type", "add")
                            put("classId", classId)
                            put("assessmentTitle", "OVERALL")
                            put("mark", newStatsProjected) // type issue workaround, assume Int is fine
                        })
                    } else if (oldStatsProjected != newStatsProjected) {
                        actions.add(buildJsonObject {
                            put("type", "update")
                            put("classId", classId)
                            put("assessmentTitle", "OVERALL")
                            put("oldMark", oldStatsProjected)
                            put("mark", newStatsProjected)
                        })
                    }
                }

                fun checkAssessments(oldAsms: JsonArray?, newAsms: JsonArray?) {
                    val oldAsmMap = oldAsms?.associateBy { it.jsonObject["title"]?.jsonPrimitive?.content ?: "" } ?: emptyMap()
                    
                    newAsms?.forEach { newElement ->
                        val newAsm = newElement.jsonObject
                        val title = newAsm["title"]?.jsonPrimitive?.content ?: return@forEach
                        val newMax = getNumericValue(newAsm["maxMark"]?.jsonPrimitive?.content)
                        val newScored = getNumericValue(newAsm["scoredMark"]?.jsonPrimitive?.content)
                        
                        val newPct = if (newMax > 0) (newScored / newMax) * 100 else 0.0
                        
                        if (newPct > 0) {
                            val oldAsm = oldAsmMap[title]?.jsonObject
                            if (oldAsm == null) {
                                actions.add(buildJsonObject {
                                    put("type", "add")
                                    put("classId", classId)
                                    put("assessmentTitle", title)
                                    put("mark", newPct)
                                })
                            } else {
                                val oldMax = getNumericValue(oldAsm["maxMark"]?.jsonPrimitive?.content)
                                val oldScored = getNumericValue(oldAsm["scoredMark"]?.jsonPrimitive?.content)
                                val oldPct = if (oldMax > 0) (oldScored / oldMax) * 100 else 0.0
                                
                                if (oldPct != newPct) {
                                    actions.add(buildJsonObject {
                                        put("type", "update")
                                        put("classId", classId)
                                        put("assessmentTitle", title)
                                        put("oldMark", oldPct)
                                        put("mark", newPct)
                                    })
                                }
                            }
                        }
                    }
                }

                checkAssessments(oldGroup.theory?.get("assessments") as? JsonArray, newGroup.theory?.get("assessments") as? JsonArray)
                checkAssessments(oldGroup.lab?.get("assessments") as? JsonArray, newGroup.lab?.get("assessments") as? JsonArray)
            }

            if (actions.isNotEmpty()) {
                val userHash = hashStringSha256(username)
                val response = client.post("$API_BASE/api/marks/sync") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("actions", JsonArray(actions))
                        put("userHash", userHash)
                        put("timestamp", Clock.System.now().toEpochMilliseconds())
                    }.toString())
                }
                
                if (response.status.isSuccess()) {
                    store.setString("hasSyncedMarksV2", "true")
                }
            } else if (!hasSyncedBefore) {
                store.setString("hasSyncedMarksV2", "true")
            }

        } catch (e: Exception) {
            println("Error during background sync: ${e.message}")
        }
    }
}
