package com.amazecc.app.shared.vtop

/**
 * Where VTOP and academic data comes from.
 *
 * [REMOTE] Tier 1: Proxies through hono.amazecc.com (Hono/Cloudflare/backend remote API).
 * [LOCAL] Tier 2: Talks to VTOP directly from the device via embedded headless scraper ([VtopEngine]).
 * [OFFLINE_CACHE] Tier 3: Pure offline mode using cached snapshots from [AppDataStore] and [UserStore].
 */
enum class VtopSource(val displayName: String, val tier: Int, val description: String) {
    REMOTE("Tier 1: Remote API", 1, "Via hono.amazecc.com (Remote Backend)"),
    LOCAL("Tier 2: Local Headless Scraper", 2, "On-device embedded scraper via WebView"),
    OFFLINE_CACHE("Pure Offline Cache", 3, "Offline mode using only local persisted storage");

    val rank: Int get() = tier

    companion object {
        fun fromName(value: String?): VtopSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOCAL
    }
}
