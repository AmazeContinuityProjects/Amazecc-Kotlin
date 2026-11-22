package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.GeotagPhoto
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-173 — deterministic tests for the stateless geotag photo surface.
 *
 * No camera, pinning, sharing or file IO runs here: each test feeds a real-shaped immutable
 * snapshot and asserts only the emitted [M3EGeotagAction]s, the real overlay facts and honest copy.
 */
@OptIn(ExperimentalTestApi::class)
class M3EGeotagPhotoScreenUiTest {

    /** A real-shaped camera result: non-zero coordinates, a place, a timestamp and bytes. */
    private fun realPhoto() = GeotagPhoto(
        id = "geotag-1",
        // Small byte array: on JVM this does not decode to a bitmap, but the metadata overlay
        // must still render the real capture facts.
        imageBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03),
        latitude = 12.9716,
        longitude = 77.5946,
        placeName = "VIT Vellore",
        capturedAt = 1_786_589_400_000L,
        accuracyMeters = 12f,
    )

    @Test
    fun loadedPhotoRendersRealOverlayFactsAndEmitsActions() = runComposeUiTest {
        val actions = mutableListOf<M3EGeotagAction>()
        val photo = realPhoto()
        setContent {
            AmazeM3ETheme {
                M3EGeotagPhotoScreen(
                    state = M3EGeotagPhotoState(photo = photo),
                    onAction = { actions.add(it) },
                )
            }
        }

        onNodeWithTag(GEOTAG_PHOTO_TAG).assertExists()
        onNodeWithTag(GEOTAG_OVERLAY_TAG).assertExists()

        // Every overlay value is a real field of the captured photo — never a placeholder.
        onNodeWithText("Place").assertExists()
        onNodeWithText("VIT Vellore").assertExists()
        onNodeWithText("Latitude").assertExists()
        onNodeWithText(photo.latitude.toString()).assertExists()
        onNodeWithText("Longitude").assertExists()
        onNodeWithText(photo.longitude.toString()).assertExists()
        onNodeWithText("Captured").assertExists()
        onNodeWithText(formatCapturedAt(photo.capturedAt)).assertExists()
        onNodeWithText("Accuracy").assertExists()
        onNodeWithText(formatAccuracy(photo.accuracyMeters!!)).assertExists()
        onAllNodesWithText("N/A").assertCountEquals(0)
        onAllNodesWithText("null").assertCountEquals(0)
        onAllNodesWithText("0.0").assertCountEquals(0)

        onNodeWithText("Share").performClick()
        onNodeWithText("Pin quick photo shortcut").performClick()
        onNodeWithContentDescription("Clear photo").performClick()
        onNodeWithContentDescription("Retake photo").performClick()

        assertTrue(actions.any { it is M3EGeotagAction.Share }, "share missing; actions=$actions")
        assertTrue(actions.any { it is M3EGeotagAction.PinShortcut }, "pin missing; actions=$actions")
        assertTrue(actions.any { it is M3EGeotagAction.Clear }, "clear missing; actions=$actions")
        assertTrue(actions.any { it is M3EGeotagAction.Capture }, "capture missing; actions=$actions")
    }

    @Test
    fun emptyStateIsHonestAndEmitsCapture() = runComposeUiTest {
        val actions = mutableListOf<M3EGeotagAction>()
        setContent {
            AmazeM3ETheme {
                M3EGeotagPhotoScreen(state = M3EGeotagPhotoState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithTag(GEOTAG_EMPTY_TAG).assertExists()
        onNodeWithTag(GEOTAG_CAPTURE_TAG).assertExists()
        onNodeWithText("Take geotag photo").assertExists()
        onNodeWithTag(GEOTAG_PHOTO_TAG).assertDoesNotExist()
        onNodeWithText("Camera and location access are required", substring = true).assertExists()

        onNodeWithText("Take geotag photo").performClick()
        assertTrue(actions.any { it is M3EGeotagAction.Capture }, "capture missing; actions=$actions")
    }

    @Test
    fun errorAndLoadingCopyIsHonest() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EGeotagPhotoScreen(
                    state = M3EGeotagPhotoState(
                        isCapturing = true,
                        errorMessage = "No photo was captured. Camera or location access may be unavailable.",
                    ),
                    onAction = {},
                )
            }
        }

        onNodeWithText("Opening the camera to capture a geotagged photo…").assertExists()
        onNodeWithText("No photo was captured. Camera or location access may be unavailable.").assertExists()
        onAllNodesWithText("N/A").assertCountEquals(0)
        onAllNodesWithText("null").assertCountEquals(0)
    }

    @Test
    fun mobileRowRuleUsesAtMostTwoLabelledButtonsWithIconExtras() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EGeotagPhotoScreen(
                    state = M3EGeotagPhotoState(photo = realPhoto()),
                    onAction = {},
                )
            }
        }

        // Only two labelled buttons exist; Clear and Retake are icon buttons.
        onNodeWithText("Share").assertExists()
        onNodeWithText("Pin quick photo shortcut").assertExists()
        onAllNodesWithText("Share").assertCountEquals(1)
        onAllNodesWithText("Pin quick photo shortcut").assertCountEquals(1)
        onAllNodesWithText("Clear").assertCountEquals(0)
        onNodeWithContentDescription("Clear photo").assertExists()
        onNodeWithContentDescription("Retake photo").assertExists()
    }

    @Test
    fun helpersUseOnlyRealFacts() {
        assertEquals("amazecc-geotag-1234.jpg", geotagPhotoFileName(1234L))
        assertEquals("12 m", formatAccuracy(12f))
        assertTrue(formatCapturedAt(1_786_589_400_000L).contains("UTC"))
    }
}
