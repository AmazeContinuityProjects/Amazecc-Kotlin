package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.GeotagPhoto
import com.amazecc.app.shared.ui.m3e.components.M3EPageShell
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.GeotagMap
import com.amazecc.app.shared.utils.toImageBitmap
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal const val GEOTAG_CAPTURE_TAG = "geotag_capture"
internal const val GEOTAG_EMPTY_TAG = "geotag_empty"
internal const val GEOTAG_PHOTO_TAG = "geotag_photo"
internal const val GEOTAG_OVERLAY_TAG = "geotag_overlay"
internal const val GEOTAG_SHARE_TAG = "geotag_share"
internal const val GEOTAG_PIN_TAG = "geotag_pin"
internal const val GEOTAG_RETAKE_TAG = "geotag_retake"
internal const val GEOTAG_CLEAR_TAG = "geotag_clear"

/** Immutable snapshot handed to [M3EGeotagPhotoScreen] by [M3EGeotagPhotoHost]. */
data class M3EGeotagPhotoState(
    val photo: GeotagPhoto? = null,
    val isCapturing: Boolean = false,
    val errorMessage: String? = null,
    val pinNotice: String? = null,
    val shareNotice: String? = null
)

/** Every state change the stateless screen can request. Validation stays in the host. */
sealed interface M3EGeotagAction {
    data object Capture : M3EGeotagAction
    data object PinShortcut : M3EGeotagAction
    data object Share : M3EGeotagAction
    data object Clear : M3EGeotagAction
}

/**
 * D-173 — honest, **stateless** geotag photo surface rendered as a normal screen.
 *
 * The screen uses [M3EPageShell] for ordinary page chrome (so the bottom nav stays mounted —
 * there is no Dialog/LIFO overlay here) and reserves bottom content padding for the nav bar.
 * Only real platform facts are rendered: coordinates, place name, capture time and accuracy all
 * come from the captured [GeotagPhoto]; nothing is fabricated as a placeholder. The image is
 * decoded through the existing `ByteArray.toImageBitmap()` seam; if that decode fails the screen
 * says so while the metadata card overlay still shows the real capture data.
 *
 * This composable deliberately has no theme wrapper and no ActivityResult launcher. All those
 * seams are registered in [M3EGeotagPhotoHost], outside this stateless composition.
 */
@Composable
fun M3EGeotagPhotoScreen(
    state: M3EGeotagPhotoState,
    onAction: (M3EGeotagAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = M3ETheme.colors
    M3EPageShell(
        title = "Geotag Photos",
        eyebrow = "Campus tools",
        subtitle = "Capture a camera photo stamped with its real device location.",
        modifier = modifier,
        // Normal screen: leave room for the scaffold's bottom nav instead of covering it.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val photo = state.photo
            if (photo == null) {
                EmptyCaptureCard(state = state, colors = colors, onAction = onAction)
            } else {
                PhotoCard(photo = photo, colors = colors)
                ActionRow(onAction = onAction)
            }

            state.pinNotice?.let { GeotagFeedback(it, isError = false, colors = colors) }
            state.shareNotice?.let { GeotagFeedback(it, isError = false, colors = colors) }
            state.errorMessage?.let { GeotagFeedback(it, isError = true, colors = colors) }
        }
    }
}

@Composable
private fun EmptyCaptureCard(
    state: M3EGeotagPhotoState,
    colors: M3EColorPalette,
    onAction: (M3EGeotagAction) -> Unit,
) {
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 24.dp,
        modifier = Modifier.fillMaxWidth().testTag(GEOTAG_EMPTY_TAG),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GeotagSectionTitle("NO PHOTO YET", Icons.Rounded.PhotoCamera, colors)
            Text(
                "Take a photo with the camera to stamp it with the device's real GPS location, " +
                    "place name, capture time and accuracy.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                "Camera and location access are required. If either is unavailable, no geotag is " +
                    "captured — the app reports that instead of guessing a location.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (state.isCapturing) {
                // Honest progress: no invented coordinates, percentages or fake photo preview.
                Text(
                    "Opening the camera to capture a geotagged photo…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                )
            }
            Button(
                onClick = { onAction(M3EGeotagAction.Capture) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag(GEOTAG_CAPTURE_TAG),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Take geotag photo", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

/**
 * Photo + overlaid metadata card. The overlay is composed even when the image bytes cannot be
 * decoded, so the real capture facts are never hidden behind a decode failure.
 */
@Composable
private fun PhotoCard(photo: GeotagPhoto, colors: M3EColorPalette) {
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 24.dp,
        modifier = Modifier.fillMaxWidth().testTag(GEOTAG_PHOTO_TAG),
    ) {
        val bitmap = remember(photo.imageBytes) { runCatching { photo.imageBytes.toImageBitmap() }.getOrNull() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceContainerHighest),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Captured geotagged photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "Photo captured, but its image could not be decoded on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                )
            }

            // Card OVERLAY on the photo: every value below comes from the captured photo.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .testTag(GEOTAG_OVERLAY_TAG),
            ) {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.HIGHEST,
                    cornerRadius = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GeotagSectionTitle("LOCATION & CAPTURE", Icons.Rounded.Place, colors)
                        GeotagMap(
                            latitude = photo.latitude,
                            longitude = photo.longitude,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        photo.placeName?.takeIf { it.isNotBlank() }?.let { GeotagFact("Place", it, colors) }
                        GeotagFact("Latitude", photo.latitude.toString(), colors)
                        GeotagFact("Longitude", photo.longitude.toString(), colors)
                        GeotagFact("Captured", formatCapturedAt(photo.capturedAt), colors)
                        photo.accuracyMeters?.let { GeotagFact("Accuracy", formatAccuracy(it), colors) }
                        Text(
                            "Coordinates and time were recorded by the device at capture time.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** At most two labelled buttons per row; extra actions are icon buttons. */
@Composable
private fun ActionRow(onAction: (M3EGeotagAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = { onAction(M3EGeotagAction.Share) },
            modifier = Modifier.weight(1f).height(44.dp).testTag(GEOTAG_SHARE_TAG),
            shape = CircleShape,
        ) {
            Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Share", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
        Button(
            onClick = { onAction(M3EGeotagAction.PinShortcut) },
            modifier = Modifier.weight(1f).height(44.dp).testTag(GEOTAG_PIN_TAG),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = M3ETheme.colors.primaryContainer,
                contentColor = M3ETheme.colors.onPrimaryContainer,
            ),
        ) {
            Icon(Icons.Rounded.Place, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pin quick photo shortcut", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { onAction(M3EGeotagAction.Capture) }, modifier = Modifier.testTag(GEOTAG_RETAKE_TAG)) {
            Icon(Icons.Rounded.PhotoCamera, contentDescription = "Retake photo")
        }
        IconButton(onClick = { onAction(M3EGeotagAction.Clear) }, modifier = Modifier.testTag(GEOTAG_CLEAR_TAG)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Clear photo")
        }
    }
}

@Composable
private fun GeotagSectionTitle(text: String, icon: ImageVector, colors: M3EColorPalette) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
    }
}

@Composable
private fun GeotagFact(label: String, value: String, colors: M3EColorPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
    }
}

@Composable
private fun GeotagFeedback(text: String, isError: Boolean, colors: M3EColorPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) colors.errorContainer else colors.primaryContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Place,
            contentDescription = null,
            tint = if (isError) colors.error else colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) colors.error else colors.onPrimaryContainer,
        )
    }
}

/** Deterministic (UTC) capture-time rendering; no device-locale or timezone surprises in tests. */
internal fun formatCapturedAt(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
    val month = dt.date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    val hour = dt.time.hour.toString().padStart(2, '0')
    val minute = dt.time.minute.toString().padStart(2, '0')
    return "${dt.date.dayOfMonth} $month ${dt.date.year}, $hour:$minute UTC"
}

/** Accuracy is a real measurement from the device; render it without inventing precision. */
internal fun formatAccuracy(meters: Float): String {
    val rounded = kotlin.math.round(meters * 10f) / 10f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()} m" else "$rounded m"
}
