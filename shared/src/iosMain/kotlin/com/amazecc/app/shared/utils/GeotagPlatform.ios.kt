package com.amazecc.app.shared.utils

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.domain.GeotagPhoto

/**
 * No camera/location/MapKit bridge is wired into this iOS target. Fail closed: a camera launch
 * immediately reports "nothing captured", a pin request reports `false`, and the map slot shows
 * a neutral message with the real coordinates. Nothing here invents a photo, a pin, or a tile.
 */
@Composable
actual fun rememberGeotagCamera(onCaptured: (GeotagPhoto?) -> Unit): () -> Unit = { onCaptured(null) }

@Composable
actual fun rememberShortcutPinner(): (label: String) -> Boolean = { false }

@Composable
actual fun GeotagMap(latitude: Double, longitude: Double, modifier: Modifier) {
    Column(modifier = modifier) {
        Text("Map unavailable on this device")
        Text("Latitude: $latitude, Longitude: $longitude")
    }
}
