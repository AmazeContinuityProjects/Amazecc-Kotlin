package com.amazecc.app.shared.utils

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.amazecc.app.shared.domain.GeotagPhoto
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

// These keys are duplicated verbatim in GeotagCameraActivity (androidApp). The shared module
// cannot depend on the app module, so explicit string keys are the boundary contract.
private const val EXTRA_IMAGE_URI = "com.amazecc.app.android.geotag.IMAGE_URI"
private const val EXTRA_LATITUDE = "com.amazecc.app.android.geotag.LATITUDE"
private const val EXTRA_LONGITUDE = "com.amazecc.app.android.geotag.LONGITUDE"
private const val EXTRA_PLACE_NAME = "com.amazecc.app.android.geotag.PLACE_NAME"
private const val EXTRA_CAPTURED_AT = "com.amazecc.app.android.geotag.CAPTURED_AT"
private const val EXTRA_ACCURACY_METERS = "com.amazecc.app.android.geotag.ACCURACY_METERS"

private const val CAMERA_ACTIVITY = "com.amazecc.app.android.GeotagCameraActivity"
private const val MAIN_ACTIVITY = "com.amazecc.app.android.MainActivity"

@Composable
actual fun rememberGeotagCamera(onCaptured: (GeotagPhoto?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
            onCaptured(null)
            return@rememberLauncherForActivityResult
        }
        val uriValue = data.getStringExtra(EXTRA_IMAGE_URI)
        if (uriValue.isNullOrBlank()) {
            onCaptured(null)
            return@rememberLauncherForActivityResult
        }
        // Read the real JPEG from the content URI off Main; the activity never packs bytes
        // into the result Intent (Binder caps a transaction at roughly 1 MB).
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = runCatching {
                appContext.contentResolver.openInputStream(android.net.Uri.parse(uriValue))?.use { it.readBytes() }
            }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (bytes == null || bytes.isEmpty()) {
                    onCaptured(null)
                    return@withContext
                }
                onCaptured(
                GeotagPhoto(
                    imageBytes = bytes,
                    latitude = data.getDoubleExtra(EXTRA_LATITUDE, 0.0),
                    longitude = data.getDoubleExtra(EXTRA_LONGITUDE, 0.0),
                    placeName = data.getStringExtra(EXTRA_PLACE_NAME),
                    capturedAt = data.getLongExtra(EXTRA_CAPTURED_AT, 0L),
                    accuracyMeters = if (data.hasExtra(EXTRA_ACCURACY_METERS)) {
                        data.getFloatExtra(EXTRA_ACCURACY_METERS, 0f)
                    } else {
                        null
                    },
                ),
            )
        }
    }
    }
    return {
        // Explicit component name — the shared module must not link against the app's Activity class.
        launcher.launch(Intent().apply { component = ComponentName(context.packageName, CAMERA_ACTIVITY) })
    }
}

@Composable
actual fun rememberShortcutPinner(): (label: String) -> Boolean {
    val context = LocalContext.current
    return { label ->
        runCatching {
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(context.packageName, MAIN_ACTIVITY)
                putExtra("target_screen", "GEOTAG_PHOTO")
                data = Uri.parse("amazecc://geotag")
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val shortcut = ShortcutInfoCompat.Builder(context, "geotag-photo")
                .setShortLabel(label.ifBlank { "Geotag photo" })
                .setLongLabel(label.ifBlank { "Capture a geotagged photo" })
                .setIcon(IconCompat.createWithResource(context, context.applicationInfo.icon))
                .setIntent(launchIntent)
                .build()
            // false means the launcher refused the request — never report a fake success.
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }
}

@Composable
actual fun GeotagMap(latitude: Double, longitude: Double, modifier: Modifier) {
    val context = LocalContext.current
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        // OSM tile policy requires an identifying user agent; keep tiles in the app cache so no
        // external-storage permission is needed.
        Configuration.getInstance().apply {
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            userAgentValue = context.packageName
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapRef.value?.let { map ->
                runCatching { map.onPause() }
                runCatching { map.onDetach() } // osmdroid 6.1.x has no destroy()/onDestroy()
            }
            mapRef.value = null
        }
    }

    AndroidView<FrameLayout>(
        modifier = modifier,
        factory = { ctx ->
            val map = MapView(ctx).apply {
                setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                val point = GeoPoint(latitude, longitude)
                controller.setCenter(point)
                overlays.add(
                    Marker(this).apply {
                        position = point
                        title = "Capture location"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                )
                runCatching { onResume() }
            }
            mapRef.value = map
            FrameLayout(ctx).apply {
                addView(map, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                // Explicit attribution; osmdroid also draws its own, but OSM policy is worth being certain about.
                addView(
                    TextView(ctx).apply {
                        text = "© OpenStreetMap contributors"
                        textSize = 10f
                        setTextColor(Color.DKGRAY)
                        setBackgroundColor(Color.parseColor("#CCFFFFFF"))
                        setPadding(6, 3, 6, 3)
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START,
                    ).apply {
                        bottomMargin = 4
                        leftMargin = 4
                    },
                )
            }
        },
        update = {
            val point = GeoPoint(latitude, longitude)
            mapRef.value?.let { map ->
                map.controller.setCenter(point)
                (map.overlays.firstOrNull { overlay -> overlay is Marker } as? Marker)?.position = point
                map.invalidate()
            }
        },
    )
}
