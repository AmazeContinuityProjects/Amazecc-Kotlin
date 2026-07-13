package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class CaptchaResponse(
    val captchaType: String, // "GRECAPTCHA" | "DEFAULT"
    val captchaBase64: String? = null,
    val cookies: List<String> = emptyList(),
    val csrf: String? = null
)

@Serializable
data class CaptchaResponseError(
    val error: String
)

@Serializable
data class LoginRequestBody(
    val username: String,
    val password: String,
    val captcha: String,
    val csrf: String,
    val cookies: List<String>
)
