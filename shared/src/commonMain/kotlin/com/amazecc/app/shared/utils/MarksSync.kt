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


