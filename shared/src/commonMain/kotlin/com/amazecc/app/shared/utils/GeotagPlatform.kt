package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.domain.GeotagPhoto

/**
 * D-167 — platform seams for the geotagged camera feature.
 *
 * Follows the same pattern as [rememberPhotoCapture]: tiny `@Composable expect`s registered
 * in the *host* composition so their ActivityResult launchers are never owned by a dialog.
 *
 * Every actual must fail closed. On a device without a camera/location stack,
 * [rememberGeotagCamera] calls back with `null` immediately and [rememberShortcutPinner]
 * returns `false`; [GeotagMap] renders a neutral "unavailable" message rather than a fake map.
 */

/** Opens the device camera; delivers a real [GeotagPhoto] or `null` on cancel/failure. */
@Composable
expect fun rememberGeotagCamera(onCaptured: (GeotagPhoto?) -> Unit): () -> Unit

/** Pins a home-screen shortcut; `true` only when the platform confirms the pin request. */
@Composable
expect fun rememberShortcutPinner(): (label: String) -> Boolean

/** Real map surface where available (Android/osmdroid), honest placeholder elsewhere. */
@Composable
expect fun GeotagMap(latitude: Double, longitude: Double, modifier: Modifier = Modifier)
