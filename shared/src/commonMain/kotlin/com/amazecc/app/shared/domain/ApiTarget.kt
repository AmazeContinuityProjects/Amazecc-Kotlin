package com.amazecc.app.shared.domain

/**
 * Which API mount the app sends requests to (web parity — the Primary / Backup
 * selector in `fetch-utils.ts`, exposed as a settings picker in M3E Settings).
 *
 * [PRIMARY] is the default (the current `AmazeClient.baseUrl`); [BACKUP] is the
 * legacy failover host; [CUSTOM] is whatever the user typed into the API URL
 * field. UniCC is intentionally not offered: the web app refuses it as the
 * global backup because it 404s the un-ported routes.
 */
enum class ApiTarget(val label: String, val url: String?) {
    PRIMARY("Primary (recommended)", "https://hono.amazecc.com"),
    BACKUP("Backup (legacy mirror)", "https://api.amazecc.com"),
    // CUSTOM has no built-in URL — the user's typed Worker Base URL is kept.
    CUSTOM("Custom URL", null);

    companion object {
        fun fromName(name: String?): ApiTarget =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PRIMARY
    }
}
