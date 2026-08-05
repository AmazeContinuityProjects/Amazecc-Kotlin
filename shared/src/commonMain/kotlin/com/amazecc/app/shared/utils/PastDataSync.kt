package com.amazecc.app.shared.utils

import com.amazecc.app.shared.api.AmazeClient
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

object PastDataSync {

    suspend fun syncPastSemesters(
        allGradesDataStr: String?,
        creds: JsonObject?,
        store: KeyValueStore,
        client: HttpClient
    ) {
        if (allGradesDataStr.isNullOrEmpty() || creds == null) return

        val allGradesData = try { Json.parseToJsonElement(allGradesDataStr).jsonObject } catch (e: Exception) { return }
        
        val pastSemesters = mutableListOf<String>()
        val grades = allGradesData["grades"]
        
        if (grades is JsonArray) {
            grades.forEach { element ->
                val sem = element.jsonObject
                val semId = sem["semesterSubId"]?.jsonPrimitive?.content 
                    ?: sem["semSubId"]?.jsonPrimitive?.content 
                    ?: sem["semesterId"]?.jsonPrimitive?.content
                if (semId != null) {
                    pastSemesters.add(semId)
                }
            }
        } else if (grades is JsonObject) {
            pastSemesters.addAll(grades.keys)
        }
        
        if (pastSemesters.isEmpty()) return

        for (semId in pastSemesters) {
            if (semId == "Current" || semId == "curriculum" || semId == "effectiveGrades") continue
            
            val attKey = "frozen_att_$semId"
            val marksKey = "frozen_marks_$semId"

            if (store.getString(attKey) == null || store.getString(marksKey) == null) {
                println("Fetching frozen data for past semester: $semId")
                try {
                    val response = client.post("${AmazeClient.baseUrl}/api/attendance") {
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("cookies", creds["cookies"] ?: JsonNull)
                            put("authorizedID", creds["authorizedID"] ?: JsonNull)
                            put("csrf", creds["csrf"] ?: JsonNull)
                            put("semesterId", semId)
                        }.toString())
                    }

                    if (response.status.isSuccess()) {
                        val bodyText = response.bodyAsText()
                        val data = Json.parseToJsonElement(bodyText).jsonObject
                        
                        val attRes = data["attRes"]?.jsonObject
                        if (attRes?.containsKey("attendance") == true) {
                            store.setString(attKey, attRes.toString())
                        }
                        
                        val marksRes = data["marksRes"]?.jsonObject
                        if (marksRes?.containsKey("courses") == true) {
                            store.setString(marksKey, marksRes.toString())
                        }
                    }
                } catch (err: Exception) {
                    println("Failed to fetch frozen data for $semId: ${err.message}")
                }
            }
        }
    }

    fun loadFrozenPastSemesters(
        allGradesDataStr: String?,
        store: KeyValueStore
    ): Map<String, Map<String, JsonObject?>> {
        val frozenData = mutableMapOf<String, Map<String, JsonObject?>>()
        
        if (allGradesDataStr.isNullOrEmpty()) return frozenData

        val allGradesData = try { Json.parseToJsonElement(allGradesDataStr).jsonObject } catch (e: Exception) { return frozenData }
        
        val pastSemesters = mutableListOf<String>()
        val grades = allGradesData["grades"]
        
        if (grades is JsonArray) {
            grades.forEach { element ->
                val sem = element.jsonObject
                val semId = sem["semesterSubId"]?.jsonPrimitive?.content 
                    ?: sem["semSubId"]?.jsonPrimitive?.content 
                    ?: sem["semesterId"]?.jsonPrimitive?.content
                if (semId != null) {
                    pastSemesters.add(semId)
                }
            }
        } else if (grades is JsonObject) {
            pastSemesters.addAll(grades.keys)
        }

        for (semId in pastSemesters) {
            if (semId == "Current" || semId == "curriculum" || semId == "effectiveGrades") continue
            
            val attKey = "frozen_att_$semId"
            val marksKey = "frozen_marks_$semId"

            val attStr = store.getString(attKey)
            val marksStr = store.getString(marksKey)

            if (attStr != null || marksStr != null) {
                try {
                    frozenData[semId] = mapOf(
                        "attendance" to if (attStr != null) Json.parseToJsonElement(attStr).jsonObject else null,
                        "marks" to if (marksStr != null) Json.parseToJsonElement(marksStr).jsonObject else null
                    )
                } catch (e: Exception) {
                    println("Failed to parse frozen data for $semId")
                }
            }
        }

        return frozenData
    }
}
