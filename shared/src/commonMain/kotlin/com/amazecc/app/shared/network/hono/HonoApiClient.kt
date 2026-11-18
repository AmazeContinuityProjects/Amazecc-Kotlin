package com.amazecc.app.shared.network.hono

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface HonoApiClient {
    suspend fun login(request: HonoLoginRequest): Result<HonoLoginResponse>
    suspend fun getIdentity(token: String): Result<HonoUserIdentity>
    suspend fun getAcademics(token: String, semesterId: String): Result<HonoAcademicsOverview>
}

class DefaultHonoApiClient(
    private val baseUrl: String = "https://hono.amazecc.com",
    private val client: HttpClient = createDefaultHttpClient()
) : HonoApiClient {

    override suspend fun login(request: HonoLoginRequest): Result<HonoLoginResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response: HonoLoginResponse = client.post("$baseUrl/api/remote/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
                response
            }
        }

    override suspend fun getIdentity(token: String): Result<HonoUserIdentity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response: HonoUserIdentity = client.get("$baseUrl/api/remote/whoami") {
                    header("Authorization", "Bearer $token")
                }.body()
                response
            }
        }

    override suspend fun getAcademics(token: String, semesterId: String): Result<HonoAcademicsOverview> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response: HonoAcademicsOverview = client.get("$baseUrl/api/remote/academics") {
                    header("Authorization", "Bearer $token")
                    header("X-Semester-Id", semesterId)
                }.body()
                response
            }
        }

    companion object {
        fun createDefaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }
        }
    }
}
