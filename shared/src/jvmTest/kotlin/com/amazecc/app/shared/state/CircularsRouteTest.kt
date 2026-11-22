package com.amazecc.app.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-161: the circulars client was canonicalized from the legacy `/api/circulars` mount to the
 * Hono `/api/remote/circulars` mount. Unscoped HTTP traffic is attributed to a [SyncModule] by
 * [inferModule] so the network gate can log and cancel it; both mounts must still resolve to
 * [SyncModule.CIRCULARS], otherwise the canonical calls become unattributed bypasses.
 *
 * Pure/URL-string only — no HTTP is performed.
 */
class CircularsRouteTest {

    @Test
    fun `legacy circulars path infers CIRCULARS`() {
        assertEquals(SyncModule.CIRCULARS, inferModule("/api/circulars"))
    }

    @Test
    fun `canonical remote circulars path infers CIRCULARS`() {
        assertEquals(SyncModule.CIRCULARS, inferModule("/api/remote/circulars"))
    }

    @Test
    fun `legacy circulars download path infers CIRCULARS`() {
        assertEquals(SyncModule.CIRCULARS, inferModule("/api/circulars/download"))
    }

    @Test
    fun `canonical remote circulars download path infers CIRCULARS`() {
        assertEquals(SyncModule.CIRCULARS, inferModule("/api/remote/circulars/download"))
    }

    @Test
    fun `attribution works on full urls with query strings and mixed casing`() {
        assertEquals(
            SyncModule.CIRCULARS,
            inferModule("https://hono.amazecc.com/api/remote/circulars/DOWNLOAD?circularId=abc123"),
        )
    }

    @Test
    fun `adding the remote case does not steal other remote or legacy routes`() {
        // The pre-existing special cases must still win in their own branches.
        assertEquals(SyncModule.STUDENT_PROFILE, inferModule("/api/remote/whoami"))
        assertEquals(SyncModule.CURRICULUM, inferModule("/api/curriculum"))
    }
}
