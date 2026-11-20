package com.amazecc.app.shared.domain

/**
 * Which API mount the app sends requests to (web parity — the Primary / Backup
 * selector in `fetch-utils.ts`, exposed as a settings picker in M3E Settings).
 *
 * [PRIMARY] is the default (the current `AmazeClient.baseUrl`); [BACKUP] is the
 * legacy failover host; [CUSTOM] is whatever the user typed into the API URL
 * field. UniCC is intentionally not offered: the web app refuses it as the
 * global backup because it 404s the un-ported routes. It still exists in the
 * declarative [ApiMounts] list as the last-resort fallback for the ops it serves.
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

    /** The [ApiMounts] id this target floats to the front of per-op resolution. */
    val mountId: String get() = when (this) {
        PRIMARY -> "hono"
        BACKUP -> "legacy"
        CUSTOM -> "custom"
    }
}

// ── Declarative mount list (D-015) ────────────────────────────────────────────

/** Auth domain an operation needs. Mirrors the web's `ApiAuth` (`vtop`/`eventhub`/`none`/`amazeId`). */
enum class ApiAuth { VTOP, EVENTHUB, NONE, AMAZE_ID }

data class ApiMountRoute(
    val method: String,
    /** Path relative to the mount base URL, e.g. `/api/services/curves`. */
    val path: String,
    /** A route may narrow the mount's auth (hono's `/api/cabshare-routes` etc.). Null = inherit. */
    val auth: ApiAuth? = null,
)

/**
 * One declarative API mount — plain data plus a small route function, exactly the web's
 * `ApiDefinition` (`src/lib/apis/-ts`). No client/framework dependency, so resolution is
 * testable on the JVM without a network.
 */
data class ApiMount(
    val id: String,
    val label: String,
    val baseUrl: String,
    /** Auth domains this mount can serve. */
    val auth: Set<ApiAuth>,
    val routeFor: (String) -> ApiMountRoute?,
)

/** A mount + concrete URL for one operation, in the order it should be tried. */
data class ResolvedApiMount(
    val mountId: String,
    val baseUrl: String,
    val method: String,
    val path: String,
) {
    val url: String get() = baseUrl.trimEnd('/') + path
}

/**
 * The ordered mount registry: `cache → hono → legacy → unicc` (web `src/lib/apis/spec.json`).
 *
 * Resolution is *per operation and per auth domain*: a mount is only a candidate if it declares
 * the required auth AND actually serves the op. This is why `curves` resolves to Hono only (the
 * legacy mount returns no route for it) and why Amaze-ID ops never fall back to legacy/UniCC.
 * Failover itself stays in `AmazeClient.mountedPost` and uses the same 5xx/transport-only rule
 * as the Koha path (D-014).
 */
object ApiMounts {
    const val CUSTOM_ID = "custom"

    /** Default resolution order, from `spec.json`. */
    val ORDER: List<String> = listOf("cache", "hono", "legacy", "unicc")

    private val SERVICE_OPS = setOf(
        "curves", "marks/tokens", "enroll", "faculty/scrape", "faculty/schools"
    )
    private val AMAZE_ID_OPS = setOf(
        "cabshare/auth", "cabshare/trips", "cabshare/trips/me", "cabshare/hubs",
        "cabshare/match", "cabshare/waitlist", "cabshare/stats", "cabshare/ratings",
        "cabshare/notifications", "cabshare/blocks",
        "social/identity/sync", "social/people", "social/timetable", "social/pair/claim",
        "social/grant/revoke", "social/grant/visibility", "social/semester", "social/ffc-match",
        "profile/phone",
    )
    private val UNICC_ALLOWLIST = setOf(
        "login", "grades", "all-grades", "attendance", "calendar", "schedule",
        "hostel", "lms-data", "vitol-data",
    )
    private val CACHE_ROUTES = mapOf(
        "buses" to ApiMountRoute("GET", "/transport/buses"),
        "fresher-resources" to ApiMountRoute("GET", "/fresher/resources"),
    )

    /** Shared route shape for Hono and a user CUSTOM mount (both use the Worker op layout). */
    private fun workerRoute(op: String): ApiMountRoute = when {
        SERVICE_OPS.contains(op) -> ApiMountRoute("POST", "/api/services/$op")
        AMAZE_ID_OPS.contains(op) || op.startsWith("cabshare/") ||
            op.startsWith("profile/") || op.startsWith("social/") ->
            ApiMountRoute("POST", "/api/$op", ApiAuth.AMAZE_ID)
        else -> ApiMountRoute("POST", "/api/$op")
    }

    val MOUNTS: List<ApiMount> = listOf(
        ApiMount("cache", "AmazeCC Cache (Public)", "https://cache.amazecc.com", setOf(ApiAuth.NONE)) { op ->
            CACHE_ROUTES[op]
        },
        ApiMount(
            "hono", "AmazeCC", "https://hono.amazecc.com",
            setOf(ApiAuth.VTOP, ApiAuth.EVENTHUB, ApiAuth.NONE, ApiAuth.AMAZE_ID),
            ::workerRoute,
        ),
        ApiMount(
            "legacy", "AmazeCC (Backup)", "https://api.amazecc.com",
            setOf(ApiAuth.VTOP, ApiAuth.EVENTHUB, ApiAuth.NONE)
        ) { op ->
            // Legacy has no /services-routes prefix; it has no curves route at all.
            if (op == "curves") null else ApiMountRoute("POST", "/api/$op")
        },
        ApiMount(
            "unicc", "UniCC (third-party fallback)", "https://api-unicc.arya22.dev",
            setOf(ApiAuth.VTOP)
        ) { op ->
            if (UNICC_ALLOWLIST.contains(op)) ApiMountRoute("POST", "/api/$op") else null
        },
    )

    /**
     * Ordered candidates for [op] requiring [auth].
     *
     * [preferredId] (from the user's [ApiTarget]) floats one mount to the front; it never removes
     * the others, so a broken preferred mount still fails over. [customBaseUrl] injects a CUSTOM
     * mount using the Worker op layout when the user typed one.
     */
    fun resolve(
        op: String,
        auth: ApiAuth,
        preferredId: String? = null,
        customBaseUrl: String? = null,
    ): List<ResolvedApiMount> {
        val byId = MOUNTS.associateBy { it.id }
        val order = buildList {
            if (!customBaseUrl.isNullOrBlank()) add(CUSTOM_ID)
            if (preferredId != null && preferredId != CUSTOM_ID) add(preferredId)
            addAll(ORDER)
        }.distinct()

        val customMount = if (customBaseUrl.isNullOrBlank()) null else ApiMount(
            CUSTOM_ID, "Custom URL", customBaseUrl.trimEnd('/'),
            setOf(ApiAuth.VTOP, ApiAuth.EVENTHUB, ApiAuth.NONE, ApiAuth.AMAZE_ID), ::workerRoute,
        )

        return order.mapNotNull { id ->
            val mount = (if (id == CUSTOM_ID) customMount else byId[id]) ?: return@mapNotNull null
            val route = mount.routeFor(op) ?: return@mapNotNull null
            if (auth !in mount.auth) return@mapNotNull null
            if (route.auth != null && auth != route.auth) return@mapNotNull null
            ResolvedApiMount(mount.id, mount.baseUrl.trimEnd('/'), route.method, route.path)
        }
    }
}
