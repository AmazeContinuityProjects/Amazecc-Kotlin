package com.amazecc.app.shared.ui.screens.events

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.launch

@Composable
fun EventHubScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppState.syncEventsAndClubs()
        if (AmazeClient.eventLogin() != null) loggedIn = true
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Events",
            description = "Discover tech fests and meetups",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = {
                scope.launch { AppState.syncEventsAndClubs(); AmazeClient.eventLogin() }
            }
        )
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSpacer()
            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                EventsTab()
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Events Tab
// ═══════════════════════════════════════════

@Composable
private fun EventsTab() {
    val colors = AmazeTheme.colors
    val eventRes by AppState.events.collectAsState()
    val events = eventRes?.events ?: emptyList()
    var selectedEvent by remember { mutableStateOf<EventHubEvent?>(null) }
    var registeredEvents by remember { mutableStateOf(setOf<String>()) }
    val categories = listOf("All", "Technical", "Cultural", "Workshop", "Hackathon")
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredEvents = remember(events, selectedCategory) {
        if (selectedCategory == "All") events
        else events.filter { it.type.equals(selectedCategory, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (eventRes == null) {
                item { LoadingState() }
            } else if (events.isEmpty()) {
                item { EmptyState() }
            } else {
                item {
                    val featured = filteredEvents.firstOrNull()
                    if (featured != null) {
                        FeaturedEventCard(
                            event = featured,
                            onViewDetails = { selectedEvent = featured }
                        )
                    }
                }
                item { CategoryFilter(categories, selectedCategory) { selectedCategory = it } }
                if (filteredEvents.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text("No events in this category", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        }
                    }
                } else {
                    items(filteredEvents.drop(1), key = { it.eid }) { event ->
                        EventCard(
                            event = event,
                            isRegistered = event.eid in registeredEvents,
                            onClick = { selectedEvent = event },
                            onRegister = { registeredEvents = registeredEvents + event.eid }
                        )
                    }
                }
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            isRegistered = event.eid in registeredEvents,
            onDismiss = { selectedEvent = null },
            onRegister = { registeredEvents = registeredEvents + event.eid }
        )
    }
}

@Composable
private fun LoadingState() {
    val colors = AmazeTheme.colors
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
            Text("Loading events...", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = AmazeTheme.colors
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.EventBusy, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
            Text("No events available", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
            Text("Check back later or sync from the header", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        }
    }
}

@Composable
private fun CategoryFilter(categories: List<String>, selectedCategory: String, onSelect: (String) -> Unit) {
    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val sel = selectedCategory == cat
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                    .background(if (sel) colors.accent else colors.surface)
                    .border(1.dp, if (sel) colors.accent else colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(cat, color = if (sel) Color.White else colors.textSecondary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
}

// ═══════════════════════════════════════════
//  Event Image Loader
// ═══════════════════════════════════════════

@Composable
private fun EventImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onLoading: @Composable () -> Unit = {},
    onFailure: @Composable () -> Unit = {}
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        isLoading = true
        bitmap = null
        val bytes = AmazeClient.getImageBytes(url)
        bitmap = bytes?.toImageBitmap()
        isLoading = false
    }

    if (isLoading) {
        onLoading()
    } else {
        val bm = bitmap
        if (bm != null) {
            Image(
                bitmap = bm,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            onFailure()
        }
    }
}

@Composable
private fun Base64EventImage(
    imageSrc: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onLoading: @Composable () -> Unit = {},
    onFailure: @Composable () -> Unit = {}
) {
    val bitmap = remember(imageSrc) {
        val base64 = imageSrc.substringAfter("base64,")
        val bytes = try { base64.decodeBase64Bytes() } catch (e: Exception) { null }
        bytes?.toImageBitmap()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        onFailure()
    }
}

// ═══════════════════════════════════════════
//  Featured Event Card
// ═══════════════════════════════════════════

@Composable
private fun FeaturedEventCard(
    event: EventHubEvent,
    onViewDetails: () -> Unit
) {
    val colors = AmazeTheme.colors
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onViewDetails)) {
        val imgUrl = event.posterUrl?.takeIf { it.isNotEmpty() } ?: "https://eventhubcc.vit.ac.in/EventHub/image/?id=${event.eid}"
        EventImage(
            url = imgUrl,
            contentDescription = "Featured Event Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            onLoading = { Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(colors.accent.copy(alpha = 0.1f))) },
            onFailure = { Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(colors.accent.copy(alpha = 0.1f))) }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(event.type, color = colors.accent.copy(alpha = 0.9f), style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(4.dp))
            Text(event.title, color = Color.White, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black))
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarToday, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(event.date, color = Color.White.copy(alpha = 0.7f), style = AmazeTheme.typography.caption.copy())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(event.location, color = Color.White.copy(alpha = 0.7f), style = AmazeTheme.typography.caption.copy())
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd).padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (event.price == "Free") colors.success.copy(alpha = 0.85f) else colors.accent.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(event.price, color = Color.White, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs))
        }
    }
}

// ═══════════════════════════════════════════
//  Event Card
// ═══════════════════════════════════════════

@Composable
private fun EventCard(
    event: EventHubEvent,
    isRegistered: Boolean,
    onClick: () -> Unit,
    onRegister: () -> Unit
) {
    val colors = AmazeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imgUrl = event.posterUrl?.takeIf { it.isNotEmpty() } ?: "https://eventhubcc.vit.ac.in/EventHub/image/?id=${event.eid}"
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                EventImage(
                    url = imgUrl,
                    contentDescription = "Event Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onLoading = { Icon(Icons.Rounded.Event, null, tint = colors.accent.copy(alpha = 0.5f), modifier = Modifier.size(28.dp)) },
                    onFailure = { Icon(Icons.Rounded.Event, null, tint = colors.accent.copy(alpha = 0.5f), modifier = Modifier.size(28.dp)) }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.accent.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(event.type, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(event.date, style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.xs))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    event.title,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(event.location, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isRegistered) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.successText, modifier = Modifier.size(28.dp))
                    Text("Registered", style = AmazeTheme.typography.smallLabel.copy(color = colors.successText, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro))
                } else {
                    Text(event.price, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.accent, fontSize = AmazeTheme.fontSize.lg))
                    if (event.eligibility.isNotEmpty()) {
                        Text(event.eligibility, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Zoomable Poster Viewer
// ═══════════════════════════════════════════

@Composable
private fun ZoomablePosterViewer(
    url: String?,
    base64Src: String?,
    onDismiss: () -> Unit
) {
    var bitmap by remember(url, base64Src) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url, base64Src) { mutableStateOf(true) }

    LaunchedEffect(url, base64Src) {
        isLoading = true
        bitmap = null
        if (base64Src != null) {
            val base64 = base64Src.substringAfter("base64,")
            val bytes = try { base64.decodeBase64Bytes() } catch (_: Exception) { null }
            bitmap = bytes?.toImageBitmap()
        } else if (url != null) {
            val bytes = AmazeClient.getImageBytes(url)
            bitmap = bytes?.toImageBitmap()
        }
        isLoading = false
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDismiss() },
                    onDoubleTap = {
                        if (scale > 1.5f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 3f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        } else {
            val bm = bitmap
            if (bm != null) {
                Image(
                    bitmap = bm,
                    contentDescription = "Event Poster",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ImageNotSupported, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Poster unavailable", color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Event Detail Bottom Sheet
// ═══════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(
    event: EventHubEvent,
    isRegistered: Boolean,
    onDismiss: () -> Unit,
    onRegister: () -> Unit
) {
    val colors = AmazeTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewData by remember { mutableStateOf<EventHubPreview?>(null) }
    var isLoadingPreview by remember { mutableStateOf(true) }
    var registrationRes by remember { mutableStateOf<EventHubRegisterRes?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    var showPosterViewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(event.eid) {
        isLoadingPreview = true
        previewData = AmazeClient.getEventPreview(event.eid)
        isLoadingPreview = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        // Extract poster sources once for use in both the column and viewer overlay
        val posterBase64Src = previewData?.imageSrc?.takeIf { it.isNotEmpty() }
        val posterImgUrl = event.posterUrl?.takeIf { it.isNotEmpty() }
        val fallbackImgUrl = "https://eventhubcc.vit.ac.in/EventHub/image/?id=${event.eid}"

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Poster Image ──

                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(240.dp)
                        .background(colors.accent.copy(alpha = 0.06f))
                        .clickable { showPosterViewer = true }
                ) {
                val loadingContent: @Composable () -> Unit = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(28.dp))
                    }
                }
                val failureContent: @Composable () -> Unit = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.ImageNotSupported, null, tint = colors.textMuted.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Poster unavailable", style = AmazeTheme.typography.caption.copy(color = colors.textMuted.copy(alpha = 0.5f)))
                        }
                    }
                }

                if (posterBase64Src != null) {
                    Base64EventImage(
                        imageSrc = posterBase64Src,
                        contentDescription = "Event Poster",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onLoading = loadingContent,
                        onFailure = failureContent
                    )
                } else if (posterImgUrl != null) {
                    EventImage(
                        url = posterImgUrl,
                        contentDescription = "Event Poster",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onLoading = loadingContent,
                        onFailure = failureContent
                    )
                } else {
                    EventImage(
                        url = fallbackImgUrl,
                        contentDescription = "Event Poster",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onLoading = loadingContent,
                        onFailure = failureContent
                    )
                }

                // Gradient overlay at bottom for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, colors.surface)))
                )
            }

            // ── Event Info Section ──
            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp)) {
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.title,
                            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.xl)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.accent.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(event.type, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, color = colors.accent)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (event.price == "Free") colors.success.copy(alpha = 0.1f)
                                        else colors.accent.copy(alpha = 0.1f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    event.price,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AmazeTheme.fontSize.sm,
                                    color = if (event.price == "Free") colors.success else colors.accent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Meta Details ──
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetaRow(Icons.Rounded.CalendarToday, "Date", event.date)
                    if (event.time != null) MetaRow(Icons.Rounded.Schedule, "Time", event.time)
                    MetaRow(Icons.Rounded.LocationOn, "Venue", event.location)
                    MetaRow(Icons.Rounded.People, "Eligibility", event.eligibility)
                }

                // ── Preview description ──
                if (previewData?.description?.isNotEmpty() == true) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("About", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        previewData?.description ?: "",
                        style = AmazeTheme.typography.body.copy(color = colors.textSecondary, lineHeight = 22.sp)
                    )
                }

                // ── Preview extra details ──
                previewData?.metaDetails?.let { details ->
                    if (details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        details.filterKeys { it != "Event Description" }.forEach { (label, value) ->
                            DetailRow(label, value)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Register Button ──
            if (registrationRes != null) {
                RegistrationResult(registrationRes!!, colors)
            } else if (isRegistered) {
                RegisteredButton(colors)
            } else {
                RegisterButton(
                    isLoading = isRegistering,
                    colors = colors,
                    onClick = {
                        isRegistering = true
                        scope.launch {
                            val res = AmazeClient.registerForEvent(event.eid)
                            if (res != null) {
                                registrationRes = res
                                onRegister()
                            }
                            isRegistering = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showPosterViewer) {
            ZoomablePosterViewer(
                url = posterImgUrl ?: fallbackImgUrl,
                base64Src = posterBase64Src,
                onDismiss = { showPosterViewer = false }
            )
        }
    }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = AmazeTheme.colors
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontWeight = FontWeight.Medium), modifier = Modifier.width(120.dp))
        Text(value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val colors = AmazeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.xs))
            Text(value, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.md))
        }
    }
}

@Composable
private fun RegisterButton(isLoading: Boolean, colors: com.amazecc.app.shared.theme.AmazeColors, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = colors.background, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.HowToReg, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Register Now", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.lg)
        }
    }
}

@Composable
private fun RegisteredButton(colors: com.amazecc.app.shared.theme.AmazeColors) {
    Button(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.successText, disabledContainerColor = colors.successText),
        enabled = false
    ) {
        Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Registered", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.lg)
    }
}

@Composable
private fun RegistrationResult(res: EventHubRegisterRes, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when (res.status) {
                        "success", "already_registered" -> colors.success.copy(alpha = 0.1f)
                        "payment_required", "payment_form" -> colors.warning.copy(alpha = 0.1f)
                        else -> colors.danger.copy(alpha = 0.1f)
                    }
                )
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (res.status) {
                        "success", "already_registered" -> Icons.Rounded.CheckCircle
                        "payment_required", "payment_form" -> Icons.Rounded.Payment
                        else -> Icons.Rounded.Error
                    },
                    contentDescription = null,
                    tint = when (res.status) {
                        "success", "already_registered" -> colors.success
                        "payment_required", "payment_form" -> colors.warning
                        else -> colors.danger
                    },
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    res.message ?: "Status: ${res.status}",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary)
                )
            }
        }

        val regUrl = res.url
        if (regUrl != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Button(
                onClick = { uriHandler.openUri(regUrl) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Payment Gateway", fontWeight = FontWeight.Bold)
            }
        }
    }
}
