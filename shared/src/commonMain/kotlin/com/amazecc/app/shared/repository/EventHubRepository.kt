package com.amazecc.app.shared.repository

import com.amazecc.app.shared.model.EventHubEvent
import com.amazecc.app.shared.model.EventHubPreview
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class EventHubRepository(private val client: HttpClient) {
    private val API_BASE = "https://amazecc.vercel.app" // Adjust accordingly

    suspend fun getEvents(): List<EventHubEvent> {
        return try {
            val response = client.get("$API_BASE/api/events")
            if (response.status.isSuccess()) {
                val data = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                val events = data.map { Json.decodeFromJsonElement<EventHubEvent>(it) }
                
                val uniqueEventsMap = mutableMapOf<String, EventHubEvent>()
                events.forEach { event ->
                    if (!uniqueEventsMap.containsKey(event.eid)) {
                        uniqueEventsMap[event.eid] = event
                    } else {
                        val existing = uniqueEventsMap[event.eid]!!
                        if (event.eligibility.isNotEmpty() && existing.eligibility.isNotEmpty() && !existing.eligibility.contains(event.eligibility)) {
                            uniqueEventsMap[event.eid] = existing.copy(eligibility = "${existing.eligibility}, ${event.eligibility}")
                        }
                    }
                }
                uniqueEventsMap.values.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Failed to fetch events: ${e.message}")
            throw e
        }
    }

    suspend fun getEventPreview(jsessionid: String, eid: String): EventHubPreview {
        try {
            val response = client.post("$API_BASE/api/events/preview") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("jsessionid", jsessionid)
                    put("eid", eid)
                }.toString())
            }
            if (response.status.isSuccess()) {
                return Json.decodeFromString<EventHubPreview>(response.bodyAsText())
            } else {
                throw Exception("Failed to load event preview (status: ${response.status})")
            }
        } catch (e: Exception) {
            println("Failed to load preview: ${e.message}")
            throw e
        }
    }

    suspend fun getRegisteredEvents(jsessionid: String): List<EventHubEvent> {
        try {
            val response = client.post("$API_BASE/api/events/profile") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("jsessionid", jsessionid)
                }.toString())
            }
            if (response.status.isSuccess()) {
                val data = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                return data.map { Json.decodeFromJsonElement<EventHubEvent>(it) }
            } else {
                val errText = response.bodyAsText()
                val errMsg = try {
                    Json.parseToJsonElement(errText).jsonObject["error"]?.jsonPrimitive?.content ?: "Failed to fetch registered events"
                } catch (e: Exception) {
                    "Failed to fetch registered events"
                }
                throw Exception(errMsg)
            }
        } catch (e: Exception) {
            println("Failed to fetch registered events: ${e.message}")
            throw e
        }
    }
}
