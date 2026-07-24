package com.amazecc.app.shared.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import com.amazecc.app.shared.api.AmazeClient

@Composable
internal fun AuthKamelImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onLoading: @Composable () -> Unit = {},
    onFailure: @Composable () -> Unit = {}
) {
    var bytes by remember(url) { mutableStateOf<ByteArray?>(null) }
    var loadState by remember(url) { mutableStateOf<AuthImageState>(AuthImageState.Loading) }

    LaunchedEffect(url) {
        loadState = AuthImageState.Loading
        bytes = null
        val result = AmazeClient.getImageBytes(url)
        if (result != null) {
            bytes = result
            loadState = AuthImageState.Success
        } else {
            loadState = AuthImageState.Error
        }
    }

    when (loadState) {
        AuthImageState.Loading -> onLoading()
        AuthImageState.Error -> onFailure()
        AuthImageState.Success -> KamelImage(
            resource = asyncPainterResource(data = bytes ?: return@AuthKamelImage),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onFailure = { onFailure() }
        )
    }
}

private enum class AuthImageState { Loading, Success, Error }

@Composable
fun EventHubScreen() {
    val colors = AmazeTheme.colors

    LaunchedEffect(Unit) {
        AppState.syncEventsAndClubs()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Events",
            description = "Discover tech fests and meetups",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::syncEventsAndClubs
        )

        Column(modifier = Modifier.fillMaxSize()) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()

            Box(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                EventsTab()
            }
        }
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  Events Tab
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (eventRes == null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading events...", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                        }
                    }
                }
            } else if (events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.EventBusy, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No events available", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                            Text("Check back later or sync from the header", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
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
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accent, selectedLabelColor = Color.White,
                                    containerColor = colors.surface, labelColor = colors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = colors.border, selectedBorderColor = Color.Transparent,
                                    enabled = true, selected = selectedCategory == cat
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (filteredEvents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No events in this category", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        }
                    }
                } else {
                    items(filteredEvents.drop(1)) { event ->
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

    // Event detail bottom sheet
    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            isRegistered = event.eid in registeredEvents,
            onDismiss = { selectedEvent = null },
            onRegister = {
                registeredEvents = registeredEvents + event.eid
            }
        )
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  Event Cards
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@Composable
private fun FeaturedEventCard(
    event: EventHubEvent,
    onViewDetails: () -> Unit
) {
    val colors = AmazeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.accent.copy(alpha = 0.15f), colors.accent.copy(alpha = 0.05f))
                )
            )
            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable(onClick = onViewDetails)
    ) {
        Column {
            val imgUrl = event.posterUrl?.takeIf { it.isNotEmpty() }
            if (imgUrl != null) {
                KamelImage(
                    resource = asyncPainterResource(data = imgUrl),
                    contentDescription = "Featured Event Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    onLoading = { Box(modifier = Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.1f))) },
                    onFailure = { Box(modifier = Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.1f))) }
                )
            } else {
                AuthKamelImage(
                    url = "https://eventhubcc.vit.ac.in/EventHub/image/?id=${event.eid}",
                    contentDescription = "Featured Event Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    onLoading = { Box(modifier = Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.1f))) },
                    onFailure = { Box(modifier = Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.1f))) }
                )
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Featured Event", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                    Badge(
                        containerColor = colors.accent.copy(alpha = 0.2f),
                        contentColor = colors.accent
                    ) {
                        Text(event.type, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                event.title,
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarToday, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(event.date, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(event.location, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(event.eligibility, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontSize = 13.sp), maxLines = 2)
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onViewDetails,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View Details", fontWeight = FontWeight.Bold)
            }
        }
        }
    }
}

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
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            val imgUrl = event.posterUrl?.takeIf { it.isNotEmpty() }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (imgUrl != null) {
                    KamelImage(
                        resource = asyncPainterResource(data = imgUrl),
                        contentDescription = "Event Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { Icon(Icons.Rounded.Event, null, tint = colors.accent, modifier = Modifier.size(26.dp)) },
                        onFailure = { Icon(Icons.Rounded.Event, null, tint = colors.accent, modifier = Modifier.size(26.dp)) }
                    )
                } else {
                    AuthKamelImage(
                        url = "https://eventhubcc.vit.ac.in/EventHub/image/?id=${event.eid}",
                        contentDescription = "Event Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { Icon(Icons.Rounded.Event, null, tint = colors.accent, modifier = Modifier.size(26.dp)) },
                        onFailure = { Icon(Icons.Rounded.Event, null, tint = colors.accent, modifier = Modifier.size(26.dp)) }
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(event.type, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.SemiBold))
                    Badge(
                        containerColor = when {
                            isRegistered -> Color(0xFF10B981).copy(alpha = 0.12f)
                            event.isPastEvent == true -> Color(0xFFEF4444).copy(alpha = 0.12f)
                            else -> colors.accent.copy(alpha = 0.1f)
                        },
                        contentColor = when {
                            isRegistered -> Color(0xFF10B981)
                            event.isPastEvent == true -> Color(0xFFEF4444)
                            else -> colors.accent
                        }
                    ) {
                        Text(
                            when {
                                isRegistered -> "Registered"
                                event.isPastEvent == true -> "Closed"
                                else -> event.price
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    event.title,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarToday, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(event.date, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(event.location, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRegister,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRegistered) Color(0xFF10B981).copy(alpha = 0.1f) else colors.accent,
                            contentColor = if (isRegistered) Color(0xFF10B981) else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        enabled = !isRegistered && event.isPastEvent != true,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            if (isRegistered) Icons.Rounded.CheckCircle else Icons.Rounded.HowToReg,
                            null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isRegistered) "Registered" else "Register",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(
                        onClick = onClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Details", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  Event Detail Bottom Sheet
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    var previewData by remember { mutableStateOf<com.amazecc.app.shared.model.EventHubPreview?>(null) }
    var isLoadingPreview by remember { mutableStateOf(true) }
    var registrationRes by remember { mutableStateOf<com.amazecc.app.shared.model.EventHubRegisterRes?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(event.eid) {
        isLoadingPreview = true
        previewData = com.amazecc.app.shared.api.AmazeClient.getEventPreview(event.eid)
        isLoadingPreview = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border)
                    .align(Alignment.CenterHorizontally)
            )

            // Poster image
            val apiPosterUrl = previewData?.posterUrl?.takeIf { it.isNotEmpty() } ?: event.posterUrl?.takeIf { it.isNotEmpty() }
            if (apiPosterUrl != null) {
                KamelImage(
                    resource = asyncPainterResource(data = apiPosterUrl),
                    contentDescription = "Event Poster",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.border.copy(alpha = 0.5f)),
                    contentScale = ContentScale.Crop,
                    onLoading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(24.dp))
                        }
                    },
                    onFailure = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ImageNotSupported, null, tint = colors.textMuted)
                        }
                    }
                )
            } else {
                AuthKamelImage(
                    url = "https://eventhubcc.vit.ac.in/EventHub/image/?id=${event.eid}",
                    contentDescription = "Event Poster",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.border.copy(alpha = 0.5f)),
                    contentScale = ContentScale.Crop,
                    onLoading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(24.dp))
                        }
                    },
                    onFailure = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ImageNotSupported, null, tint = colors.textMuted)
                        }
                    }
                )
            }

            // Title & type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        event.title,
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 22.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Badge(
                        containerColor = colors.accent.copy(alpha = 0.12f),
                        contentColor = colors.accent
                    ) {
                        Text(event.type, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                if (event.price != "Free") {
                    Text(
                        event.price,
                        style = AmazeTheme.typography.subheading.copy(
                            fontWeight = FontWeight.Black,
                            color = colors.accent,
                            fontSize = 20.sp
                        )
                    )
                }
            }

            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

            // Details
            DetailRow(Icons.Rounded.CalendarToday, "Date", event.date)
            if (event.time != null) DetailRow(Icons.Rounded.Schedule, "Time", event.time)
            DetailRow(Icons.Rounded.LocationOn, "Location", event.location)
            DetailRow(Icons.Rounded.People, "Eligibility", event.eligibility)

            if (event.isPastEvent == true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "This event has already passed.",
                            style = AmazeTheme.typography.caption.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            // Register button
            if (registrationRes != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(
                        text = registrationRes?.message ?: "Event Registration Initiated (Status: ${registrationRes?.status})",
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    val regUrl = registrationRes?.url
                    if (regUrl != null) {
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        Button(
                            onClick = { uriHandler.openUri(regUrl) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                        ) {
                            Text("Open Payment Gateway", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        isRegistering = true
                        scope.launch {
                            val res = com.amazecc.app.shared.api.AmazeClient.registerForEvent(event.eid)
                            if (res != null) {
                                registrationRes = res
                                onRegister()
                            }
                            isRegistering = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRegistered) Color(0xFF10B981) else colors.accent,
                        disabledContainerColor = colors.border
                    ),
                    enabled = !isRegistered && event.isPastEvent != true && !isRegistering
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(color = colors.background, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            if (isRegistered) Icons.Rounded.CheckCircle else Icons.Rounded.HowToReg,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isRegistered) "Registered" else "Register Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val colors = AmazeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            Text(value, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
        }
    }
}



