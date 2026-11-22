package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.domain.GeotagPhoto
import com.amazecc.app.shared.utils.rememberFileSharer
import com.amazecc.app.shared.utils.rememberGeotagCamera
import com.amazecc.app.shared.utils.rememberShortcutPinner

/**
 * D-173 — stateful host for the normal (nav-bar-visible) geotag photo screen.
 *
 * Every ActivityResult/platform seam is registered here, OUTSIDE any Dialog — this host is a
 * plain screen route in [com.amazecc.app.shared.App], so no overlay/nested theme subcomposition
 * is involved. The stateless [M3EGeotagPhotoScreen] only emits [M3EGeotagAction]s; all platform
 * calls and honest success/failure notices happen here.
 */
@Composable
fun M3EGeotagPhotoHost(modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(M3EGeotagPhotoState()) }
    val shareFile = rememberFileSharer()
    val pinShortcut = rememberShortcutPinner()

    /** Commit a real camera result, or report exactly why there is no photo. */
    fun onCaptured(photo: GeotagPhoto?) {
        state = if (photo == null) {
            state.copy(
                isCapturing = false,
                errorMessage = "No photo was captured. Camera or location access may be unavailable.",
            )
        } else {
            state.copy(
                photo = photo,
                isCapturing = false,
                errorMessage = null,
                pinNotice = null,
                shareNotice = null,
            )
        }
    }

    // Camera launcher is registered in the host composition, before any screen content.
    val launchCamera = rememberGeotagCamera { onCaptured(it) }

    fun handleAction(action: M3EGeotagAction) {
        when (action) {
            M3EGeotagAction.Capture -> {
                state = state.copy(isCapturing = true, errorMessage = null)
                launchCamera()
                // A platform that cannot capture calls back with null and onCaptured() replaces
                // the optimistic state with an honest error — no spinner is left hanging.
            }
            M3EGeotagAction.PinShortcut -> {
                val pinned = pinShortcut("Geotag photo")
                state = state.copy(
                    pinNotice = if (pinned) "Quick photo shortcut pinned."
                    else "A shortcut could not be pinned on this device.",
                )
            }
            M3EGeotagAction.Share -> {
                val photo = state.photo
                state = when {
                    photo == null -> state.copy(shareNotice = "Capture a photo before sharing it.")
                    photo.imageBytes.isEmpty() -> state.copy(shareNotice = "This photo has no image data to share.")
                    else -> {
                        val fileName = geotagPhotoFileName(photo.capturedAt)
                        val shared = shareFile(fileName, photo.imageBytes, "image/jpeg")
                        state.copy(
                            shareNotice = if (shared) "Shared $fileName."
                            else "Sharing is not available on this device.",
                        )
                    }
                }
            }
            M3EGeotagAction.Clear -> state = M3EGeotagPhotoState()
        }
    }

    M3EGeotagPhotoScreen(state = state, onAction = { handleAction(it) }, modifier = modifier)
}

/** Real, timestamp-derived filename so each shared geotag photo is uniquely identifiable. */
internal fun geotagPhotoFileName(timestampMillis: Long): String = "amazecc-geotag-$timestampMillis.jpg"
