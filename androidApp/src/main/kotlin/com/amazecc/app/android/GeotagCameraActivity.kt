package com.amazecc.app.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.amazecc.app.shared.domain.GeotagPhoto
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import android.net.Uri
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * D-167 — a real geotagged camera surface.
 *
 * This is deliberately a plain Android-views Activity (rather than Compose) so the app module
 * needs no extra Compose material dependency: CameraX's [PreviewView] is the star, with a
 * status label and Capture/Cancel buttons layered on top.
 *
 * Honesty contract:
 * - Permissions are requested at runtime; denial ends with a cancelled result.
 * - A *fresh* fused location is requested with a 10s timeout; no cached/fabricated coordinates.
 * - EXIF GPS + date are written to the temp JPEG before it is copied to MediaStore.
 * - OEM computational modes come from [ExtensionsManager] (the supported CameraX integration
 *   point). If no extension exists, the standard pipeline is used and the UI says so. We do not
 *   claim any unverified "seamless codec" behaviour.
 */
class GeotagCameraActivity : ComponentActivity() {

    companion object {
        // Duplicated verbatim in shared GeotagPlatform.android.kt — shared cannot depend on :androidApp.
        const val EXTRA_IMAGE_URI = "com.amazecc.app.android.geotag.IMAGE_URI"
        const val EXTRA_LATITUDE = "com.amazecc.app.android.geotag.LATITUDE"
        const val EXTRA_LONGITUDE = "com.amazecc.app.android.geotag.LONGITUDE"
        const val EXTRA_PLACE_NAME = "com.amazecc.app.android.geotag.PLACE_NAME"
        const val EXTRA_CAPTURED_AT = "com.amazecc.app.android.geotag.CAPTURED_AT"
        const val EXTRA_ACCURACY_METERS = "com.amazecc.app.android.geotag.ACCURACY_METERS"

        private const val LOCATION_TIMEOUT_MS = 10_000L
        private const val GEOCODE_TIMEOUT_MS = 3_000L

        /** Runs a future listener on whichever thread completed it; no extra dispatcher dependency. */
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var captureButton: Button
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        statusText = TextView(this).apply {
            text = "Requesting camera and location…"
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#B3000000"))
            setPadding(24, 16, 24, 16)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = 32 }
        }
        val capture = Button(this).apply {
            text = "Capture"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(0, 0, 16, 0) }
        }
        val cancel = Button(this).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(16, 0, 0, 0) }
        }
        captureButton = capture
        capture.setOnClickListener { onCapture() }
        cancel.setOnClickListener { deliverResult(null) }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 48)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )
            addView(capture)
            addView(cancel)
        }
        setContentView(
            FrameLayout(this).apply {
                addView(previewView)
                addView(statusText)
                addView(controls)
            }
        )

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val cameraGranted = results[Manifest.permission.CAMERA] == true
            val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            when {
                !cameraGranted -> {
                    statusText.text = "Camera permission denied"
                    deliverResult(null)
                }
                !locationGranted -> {
                    statusText.text = "Location permission denied"
                    deliverResult(null)
                }
                else -> startCamera()
            }
        }
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    private fun startCamera() {
        scope.launch {
            try {
                statusText.text = "Starting camera…"
                val provider = ProcessCameraProvider.getInstance(this@GeotagCameraActivity).awaitFuture()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                @Suppress("DEPRECATION")
                val rotation = windowManager.defaultDisplay.rotation
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(rotation)
                    .build()

                // Query OEM computational photography through the supported CameraX Extensions
                // surface. ExtensionSessionConfig only exists in CameraX 1.6+, so on the stable
                // 1.4.0 line we use getExtensionEnabledCameraSelector, with an honest fallback.
                var selector = CameraSelector.DEFAULT_BACK_CAMERA
                var extensionLabel: String? = null
                val manager = runCatching {
                    ExtensionsManager.getInstanceAsync(this@GeotagCameraActivity, provider).awaitFuture()
                }.getOrNull()
                if (manager != null) {
                    for (mode in listOf(
                        ExtensionMode.NIGHT,
                        ExtensionMode.BOKEH,
                        ExtensionMode.FACE_RETOUCH,
                        ExtensionMode.HDR,
                    )) {
                        val available = runCatching {
                            manager.isExtensionAvailable(selector, mode)
                        }.getOrDefault(false)
                        if (!available) continue
                        val extended = runCatching {
                            manager.getExtensionEnabledCameraSelector(selector, mode)
                        }.getOrNull() ?: continue
                        if (!runCatching { provider.hasCamera(extended) }.getOrDefault(false)) continue
                        selector = extended
                        extensionLabel = when (mode) {
                            ExtensionMode.NIGHT -> "Night"
                            ExtensionMode.BOKEH -> "Portrait / bokeh"
                            ExtensionMode.FACE_RETOUCH -> "Face retouch"
                            ExtensionMode.HDR -> "HDR"
                            else -> "OEM extension"
                        }
                        break
                    }
                }

                provider.unbindAll()
                try {
                    provider.bindToLifecycle(this@GeotagCameraActivity, selector, preview, capture)
                } catch (bindingError: Exception) {
                    // Some OEM extension implementations cannot combine with ImageCapture;
                    // degrade to the standard pipeline rather than leaving a dead camera.
                    extensionLabel = null
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this@GeotagCameraActivity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                }
                imageCapture = capture
                captureButton.isEnabled = true
                statusText.text = if (extensionLabel != null) {
                    "Camera ready · $extensionLabel mode"
                } else {
                    "Camera ready · standard pipeline"
                }
            } catch (error: Exception) {
                statusText.text = "Camera unavailable: ${error.message ?: "unknown error"}"
            }
        }
    }

    private fun onCapture() {
        val capture = imageCapture ?: return
        captureButton.isEnabled = false
        scope.launch {
            val location = try {
                freshLocation()
            } catch (_: Exception) {
                null
            }
            if (location == null) {
                statusText.text = "Location unavailable — no photo saved"
                captureButton.isEnabled = true
                return@launch
            }

            statusText.text = "Capturing…"
            val tempFile = File(cacheDir, "camera-images/geo-${UUID.randomUUID()}.jpg")
            tempFile.parentFile?.mkdirs()
            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this@GeotagCameraActivity),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        scope.launch {
                            try {
                                val capturedAt = System.currentTimeMillis()
                                // EXIF, file copying and MediaStore IO must not block Main.
                                val (imageUri, place) = withContext(Dispatchers.IO) {
                                    writeExif(tempFile, location, capturedAt)
                                    val uri = saveToMediaStore(tempFile, capturedAt)
                                        ?: throw IOException("MediaStore insert failed")
                                    uri to runCatching { resolvePlaceName(location) }.getOrNull()
                                }
                                deliverResult(
                                    GeotagPhoto(
                                        imageBytes = ByteArray(0), // real bytes are read from imageUri by the caller
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        placeName = place,
                                        capturedAt = capturedAt,
                                        accuracyMeters = location.accuracy,
                                    ),
                                    imageUri,
                                )
                            } catch (error: Exception) {
                                statusText.text = "Capture failed: ${error.message ?: "unknown error"}"
                                captureButton.isEnabled = true
                            } finally {
                                runCatching { if (tempFile.exists()) tempFile.delete() }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        statusText.text = "Capture failed: ${exception.message ?: "unknown error"}"
                        captureButton.isEnabled = true
                        runCatching { if (tempFile.exists()) tempFile.delete() }
                    }
                },
            )
        }
    }

    /** One fresh high-accuracy fix; timeout/cancel removes updates instead of leaving a listener. */
    @SuppressLint("MissingPermission") // permissions are requested before capture begins
    private suspend fun freshLocation(): Location? = withTimeout(LOCATION_TIMEOUT_MS) {
        val client = LocationServices.getFusedLocationProviderClient(this@GeotagCameraActivity)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMaxUpdates(1)
            .setWaitForAccurateLocation(false)
            .build()
        suspendCancellableCoroutine { cont ->
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    runCatching { client.removeLocationUpdates(this) }
                    if (cont.isActive) cont.resume(result.lastLocation)
                }
            }
            cont.invokeOnCancellation { runCatching { client.removeLocationUpdates(callback) } }
            try {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (security: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** Best-effort reverse geocode; null when offline or no address is returned (never invented). */
    @SuppressLint("MissingPermission")
    private suspend fun resolvePlaceName(location: Location): String? = withTimeout(GEOCODE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(this@GeotagCameraActivity, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let { address ->
                        address.locality ?: address.subAdminArea ?: address.adminArea ?: address.featureName
                    }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeExif(file: File, location: Location, capturedAt: Long) {
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, location.latitude.toExifRational())
        exif.setAttribute(
            ExifInterface.TAG_GPS_LATITUDE_REF,
            if (location.latitude >= 0) "N" else "S",
        )
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, location.longitude.toExifRational())
        exif.setAttribute(
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            if (location.longitude >= 0) "E" else "W",
        )
        val localDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(capturedAt))
        val utcDate = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(capturedAt))
        val utcTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(capturedAt))
        exif.setAttribute(ExifInterface.TAG_DATETIME, localDate)
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, utcDate)
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, utcTime)
        exif.setAttribute(ExifInterface.TAG_MAKE, "AmazeCC")
        exif.saveAttributes()
    }

    /** Decimal degrees -> EXIF rational "deg/1,min/1,sec*1000/1000" with absolute values. */
    private fun Double.toExifRational(): String {
        val value = abs(this)
        val degrees = value.toInt()
        val minutesTotal = (value - degrees) * 60.0
        val minutes = minutesTotal.toInt()
        val seconds = ((minutesTotal - minutes) * 60.0 * 1_000.0).roundToInt().coerceAtMost(59_999)
        return "$degrees/1,$minutes/1,$seconds/1000"
    }

    /** Copies the EXIF-stamped JPEG into the gallery; IS_PENDING keeps it hidden until complete. */
    private fun saveToMediaStore(source: File, capturedAt: Long): Uri? = runCatching {
        val name = "AMZCC_$capturedAt.jpg"
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AmazeCC")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                // App-specific external dir needs no WRITE_EXTERNAL_STORAGE on API 26-28.
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AmazeCC").apply { mkdirs() }
                put(MediaStore.Images.Media.DATA, File(dir, name).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        val written = try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) }; true } ?: false
        } catch (_: Exception) {
            false
        }
        if (!written) {
            // Never leave a pending/empty gallery row behind after a failed write.
            runCatching { resolver.delete(uri, null, null) }
            return@runCatching null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val clearPending = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, clearPending, null, null)
        }
        uri
    }.getOrNull()

    private fun deliverResult(photo: GeotagPhoto?, imageUri: Uri? = null) {
        val out = Intent()
        if (photo != null && imageUri != null) {
            // A content URI, never the full JPEG bytes: Binder transactions cap around 1 MB.
            out.putExtra(EXTRA_IMAGE_URI, imageUri.toString())
            out.putExtra(EXTRA_LATITUDE, photo.latitude)
            out.putExtra(EXTRA_LONGITUDE, photo.longitude)
            out.putExtra(EXTRA_PLACE_NAME, photo.placeName)
            out.putExtra(EXTRA_CAPTURED_AT, photo.capturedAt)
            photo.accuracyMeters?.let { out.putExtra(EXTRA_ACCURACY_METERS, it) }
            setResult(Activity.RESULT_OK, out)
        } else {
            setResult(Activity.RESULT_CANCELED, out)
        }
        finish()
    }

    private suspend fun <T> ListenableFuture<T>.awaitFuture(): T = suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    val value = get()
                    if (cont.isActive) cont.resume(value)
                } catch (error: Throwable) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            },
            DIRECT_EXECUTOR,
        )
        cont.invokeOnCancellation { cancel(false) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
