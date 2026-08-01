package com.amazecc.app.shared.repository

import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class QBankRepository(private val client: HttpClient) {
    private val API_BASE = "https://api.amazecc.com" // Adjust accordingly

    suspend fun getGlobalCourses(): List<Map<String, String>> {
        return try {
            val response = client.get("$API_BASE/api/qbank/courses")
            if (response.status.isSuccess()) {
                val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                if (data["success"]?.jsonPrimitive?.boolean == true) {
                    val coursesArray = data["data"]?.jsonArray
                    coursesArray?.mapNotNull { 
                        val obj = it.jsonObject
                        val code = obj["code"]?.jsonPrimitive?.content
                        val title = obj["title"]?.jsonPrimitive?.content
                        if (code != null && title != null) mapOf("code" to code, "title" to title) else null
                    } ?: emptyList()
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Failed to fetch global courses: ${e.message}")
            emptyList()
        }
    }

    fun extractCoursesFromUser(allGradesData: JsonObject?, marksData: JsonObject?): List<Map<String, String>> {
        val uniqueCourses = mutableMapOf<String, String>()
        
        // Past courses
        if (allGradesData != null && allGradesData.containsKey("grades")) {
            val grades = allGradesData["grades"]
            val gradesArr = if (grades is JsonArray) grades else if (grades is JsonObject) JsonArray(grades.values.toList()) else JsonArray(emptyList())
            
            for (semElement in gradesArr) {
                val sem = semElement.jsonObject
                val courseList = sem["grades"] ?: sem["courseGrades"] ?: sem["courses"] ?: continue
                val items = if (courseList is JsonArray) courseList else if (courseList is JsonObject) JsonArray(courseList.values.toList()) else JsonArray(emptyList())
                
                for (itemElement in items) {
                    val course = itemElement.jsonObject
                    val code = course["courseCode"]?.jsonPrimitive?.content ?: course["code"]?.jsonPrimitive?.content
                    val title = course["courseTitle"]?.jsonPrimitive?.content ?: course["title"]?.jsonPrimitive?.content ?: code
                    if (code != null) {
                        uniqueCourses[code] = title ?: ""
                    }
                }
            }
        }
        
        // Current courses
        if (marksData != null && marksData.containsKey("courses")) {
            val courses = marksData["courses"] as? JsonArray
            courses?.forEach { element ->
                val course = element.jsonObject
                val classIdPrefix = course["classId"]?.jsonPrimitive?.content?.split("_")?.firstOrNull()
                val code = classIdPrefix ?: course["courseCode"]?.jsonPrimitive?.content ?: course["code"]?.jsonPrimitive?.content
                val title = course["courseTitle"]?.jsonPrimitive?.content ?: course["title"]?.jsonPrimitive?.content ?: code
                if (code != null && !uniqueCourses.containsKey(code)) {
                    uniqueCourses[code] = title ?: ""
                }
            }
        }
        
        return uniqueCourses.map { mapOf("code" to it.key, "title" to it.value) }
    }
}
