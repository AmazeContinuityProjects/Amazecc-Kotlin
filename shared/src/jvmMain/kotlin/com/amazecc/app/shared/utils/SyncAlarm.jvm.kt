package com.amazecc.app.shared.utils

/**
 * Desktop/JVM has no Activity-hosted VTOP WebView, so the sync gate must degrade to cached-session
 * verification rather than attempt an interactive login that cannot be satisfied.
 */
actual fun canRunInteractiveVtop(): Boolean = false
