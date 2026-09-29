package com.amazecc.app.shared.vtop

/**
 * Where VTOP data comes from.
 *
 * [LOCAL] talks to VTOP directly from the device via [VtopEngine]. This is required for a
 * working login: VTOP only accepts connections from Indian IP space, and the app's server
 * (api.amazecc.com) runs in Singapore where every VTOP connection is dropped.
 *
 * [REMOTE] keeps the legacy behaviour of proxying through api.amazecc.com. That path is
 * currently non-functional for VTOP routes; it remains available for the endpoints backed by
 * our own Postgres (qbank, clubs, cabshare, transport, wishlist) and as an escape hatch.
 */
enum class VtopSource {
    LOCAL,
    REMOTE;

    companion object {
        fun fromName(value: String?): VtopSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOCAL
    }
}
