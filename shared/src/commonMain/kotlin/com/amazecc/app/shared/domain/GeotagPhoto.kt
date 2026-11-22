package com.amazecc.app.shared.domain

/**
 * D-167 — a photo plus the *real* capture location reported by the platform camera.
 *
 * This is a pure common model: no Android/iOS types, no EXIF parsing, no I/O. The Android
 * camera activity is responsible for reading a fresh GPS fix and stamping the JPEG before
 * it builds this value; unsupported platforms simply never produce one.
 *
 * Deliberately not `@Serializable`: the agreed common API is a plain data class so every
 * platform can construct it with named arguments. Nothing here fabricates a location —
 * [hasValidCoordinates] is the single honest "do we actually have a point?" check.
 */
data class GeotagPhoto(
    val id: String = "",
    val imageBytes: ByteArray = ByteArray(0),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeName: String? = null,
    val capturedAt: Long = 0L,
    val accuracyMeters: Float? = null,
)

/** True only when real image bytes were delivered. */
fun GeotagPhoto.hasImage(): Boolean = imageBytes.isNotEmpty()

/**
 * The default `0.0/0.0` is a "no fix" sentinel, not a real point, so it is rejected along
 * with out-of-range values. A genuine equator/prime-meridian capture is vanishingly rare
 * and the platform always supplies a non-default fix when it succeeds.
 */
fun GeotagPhoto.hasValidCoordinates(): Boolean =
    latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)

/** Human-readable coordinates for the UI, never a fabricated place name. */
fun GeotagPhoto.formatCoordinates(): String =
    if (hasValidCoordinates()) "$latitude, $longitude" else "Location unavailable"

/** A photo the rest of the app may present as a real geotagged capture. */
fun GeotagPhoto.isUsable(): Boolean = hasImage() && hasValidCoordinates()
