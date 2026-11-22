package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-167 — pure, host-independent guards for the geotagged-photo model.
 *
 * No Android device, EXIF parser or camera is involved: these are the rules the UI relies on
 * when deciding whether a result is a real geotagged capture or merely a cancelled/failed one.
 */
class GeotagPhotoTest {

    @Test
    fun defaultsAreAnHonestEmptyCapture() {
        val photo = GeotagPhoto()

        assertEquals("", photo.id)
        assertTrue(photo.imageBytes.isEmpty())
        assertEquals(0.0, photo.latitude)
        assertEquals(0.0, photo.longitude)
        assertNull(photo.placeName)
        assertEquals(0L, photo.capturedAt)
        assertNull(photo.accuracyMeters)

        assertFalse(photo.hasImage())
        assertFalse(photo.hasValidCoordinates())
        assertFalse(photo.isUsable())
        assertEquals("Location unavailable", photo.formatCoordinates())
    }

    @Test
    fun realCapturePreservesAllFieldsAndIsUsable() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val photo = GeotagPhoto(
            id = "geo-1",
            imageBytes = bytes,
            latitude = 12.9716,
            longitude = 77.5946,
            placeName = "Bengaluru",
            capturedAt = 1_700_000_000_000L,
            accuracyMeters = 4.5f,
        )

        assertEquals("geo-1", photo.id)
        assertTrue(photo.hasImage())
        assertTrue(photo.hasValidCoordinates())
        assertTrue(photo.isUsable())
        assertEquals("Bengaluru", photo.placeName)
        assertEquals(1_700_000_000_000L, photo.capturedAt)
        assertEquals(4.5f, photo.accuracyMeters)
        assertEquals("12.9716, 77.5946", photo.formatCoordinates())
    }

    @Test
    fun missingOrOutOfRangeCoordinatesAreRejected() {
        val image = byteArrayOf(1, 2, 3)

        assertFalse(GeotagPhoto(imageBytes = image).hasValidCoordinates(), "null island is not a fix")
        assertFalse(
            GeotagPhoto(imageBytes = image, latitude = 91.0, longitude = 0.0).hasValidCoordinates()
        )
        assertFalse(
            GeotagPhoto(imageBytes = image, latitude = 0.0, longitude = -181.0).hasValidCoordinates()
        )
        assertFalse(
            GeotagPhoto(imageBytes = image, latitude = Double.NaN, longitude = 10.0).hasValidCoordinates()
        )

        assertTrue(
            GeotagPhoto(imageBytes = image, latitude = -90.0, longitude = 180.0).hasValidCoordinates(),
            "range endpoints are valid"
        )
    }

    @Test
    fun imageIsRequiredForUsableAndCoordinateOnlyResultsAreNot() {
        val locationOnly = GeotagPhoto(latitude = 12.0, longitude = 77.0)
        assertTrue(locationOnly.hasValidCoordinates())
        assertFalse(locationOnly.hasImage())
        assertFalse(locationOnly.isUsable())
    }

    @Test
    fun dataClassCopyAndContentEqualityHold() {
        val photo = GeotagPhoto(
            imageBytes = byteArrayOf(1, 2, 3),
            latitude = 10.0,
            longitude = 20.0,
        )

        assertEquals(photo, photo.copy(), "ByteArray equality is by content")
        assertEquals(photo.copy(placeName = "Lab"), photo.copy(placeName = "Lab"))
        assertNotEquals(photo, photo.copy(imageBytes = byteArrayOf(9, 9, 9)))
        assertEquals(10.0, photo.copy(placeName = "Lab").latitude)
    }
}
