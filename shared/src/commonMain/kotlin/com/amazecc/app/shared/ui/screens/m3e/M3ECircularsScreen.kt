package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EPageSkeleton
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A leaf circular inside a folder. */
@Immutable
data class M3ECircularDocument(
    val id: String,
    val title: String
)

/** A grouped folder of circulars, mapped from the scraped circular tree. */
@Immutable
data class M3ECircularFolder(
    val key: String,
    val name: String,
    val documents: List<M3ECircularDocument> = emptyList()
)

/** Real, per-document outcome of the credentialed circular download. */
enum class M3ECircularDownloadStatus {
    IDLE, DOWNLOADING, SAVED, FAILED
}

/**
 * Download state for one circular, keyed by [M3ECircularDocument.id] in [M3ECircularsState].
 * [message] carries the saved file name on success, or the real failure reason on error —
 * never a fabricated success.
 */
@Immutable
data class M3ECircularDownloadUi(
    val status: M3ECircularDownloadStatus = M3ECircularDownloadStatus.IDLE,
    val message: String? = null
)

/** Stable tags used by the headless circulars UI tests. */
object M3ECircularsTestTags {
    const val CIRCULARS_LIST = "m3e_circulars_list"
    const val DOCUMENT_ROW = "m3e_circulars_document_row"
}

/** Immutable, screen-owned state for [M3ECircularsScreen]. */
@Immutable
data class M3ECircularsState(
    val folders: List<M3ECircularFolder> = emptyList(),
    val expandedKeys: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val downloadStates: Map<String, M3ECircularDownloadUi> = emptyMap()
)

/** User intents emitted by [M3ECircularsScreen]. */
sealed interface M3ECircularsAction {
    data class ToggleFolder(val key: String) : M3ECircularsAction
    /**
     * Download the circular binary with this id. The host runs the real
     * `download → openDocumentBytes` pipeline; this action is mounted on every row's
     * Download affordance.
     */
    data class OpenCircular(val id: String) : M3ECircularsAction
    data object Refresh : M3ECircularsAction
}

/**
 * Material 3 Expressive Official Circulars screen.
 * 100% stateless: receives [M3ECircularsState] and emits [M3ECircularsAction].
 * Data comes from AppState.circulars (online scraper / offline cache) via the host bridge.
 */
@Composable
fun M3ECircularsScreen(
    state: M3ECircularsState,
    onAction: (M3ECircularsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .testTag(M3ECircularsTestTags.CIRCULARS_LIST),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Official Circulars",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "${state.folders.size} announcement groups",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onAction(M3ECircularsAction.Refresh) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh circulars",
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (state.isLoading) {
            item(key = "loading") {
                M3EPageSkeleton(
                    label = "Loading circulars",
                    tiles = 2,
                    rows = 4
                )
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            item(key = "error") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error
                        )
                    }
                }
            }
        }

        if (state.folders.isEmpty()) {
            item(key = "empty") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOff,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No circulars available",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }
                }
            }
        } else {
            items(state.folders, key = { it.key }) { folder ->
                CircularFolderCard(
                    folder = folder,
                    isExpanded = folder.key in state.expandedKeys,
                    downloadStates = state.downloadStates,
                    onToggle = { onAction(M3ECircularsAction.ToggleFolder(folder.key)) },
                    onOpenDocument = { onAction(M3ECircularsAction.OpenCircular(it)) }
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(BOTTOM_NAV_PADDING))
        }
    }
}

@Composable
private fun CircularFolderCard(
    folder: M3ECircularFolder,
    isExpanded: Boolean,
    downloadStates: Map<String, M3ECircularDownloadUi>,
    onToggle: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "CircularFolderChevron"
    )

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = if (isExpanded) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableA11y(
                        onClickLabel = if (isExpanded) {
                            "Collapse folder ${folder.name}"
                        } else {
                            "Expand folder ${folder.name}"
                        },
                        mergeDescendants = false,
                        onClick = onToggle
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (folder.documents.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${folder.documents.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse folder" else "Expand folder",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer(rotationZ = rotation).size(20.dp)
                )
            }

            // D-121 / D-114: M3ETheme.motion reads the per-composition reduce-motion
            // preference; these expand/shrink specs become a true instant snap.
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = M3ETheme.motion.effectsDefault<IntSize>()),
                exit = shrinkVertically(animationSpec = M3ETheme.motion.effectsDefault<IntSize>())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (folder.documents.isEmpty()) {
                        Text(
                            text = "No items in this folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    } else {
                        folder.documents.forEach { document ->
                            val download = downloadStates[document.id]
                                ?: M3ECircularDownloadUi()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceContainerLow)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag(M3ECircularsTestTags.DOCUMENT_ROW),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = document.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    when (download.status) {
                                        M3ECircularDownloadStatus.IDLE -> Unit
                                        M3ECircularDownloadStatus.DOWNLOADING -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    color = colors.primary,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "Downloading…",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.onSurfaceVariant
                                                )
                                            }
                                        }
                                        M3ECircularDownloadStatus.FAILED -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Rounded.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = colors.error,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = download.message
                                                        ?: "Could not download this circular",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.error,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { onOpenDocument(document.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Refresh,
                                                        contentDescription = "Retry download ${document.title}",
                                                        tint = colors.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        M3ECircularDownloadStatus.SAVED -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = colors.success,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = listOfNotNull(
                                                        "Saved",
                                                        download.message?.takeIf { it.isNotBlank() }
                                                    ).joinToString(" · "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.success,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                // Real download affordance on every row: the host runs the
                                // credentialed download and opens the returned bytes.
                                IconButton(
                                    onClick = { onOpenDocument(document.id) },
                                    enabled = download.status != M3ECircularDownloadStatus.DOWNLOADING,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Download ${document.title}",
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewCircularsState() = M3ECircularsState(
    folders = listOf(
        M3ECircularFolder(
            key = "academic-2026",
            name = "Academic Announcements 2026-27",
            documents = listOf(
                M3ECircularDocument("CIR-102", "Mid-semester examination schedule"),
                M3ECircularDocument("CIR-103", "Revaluation window opens")
            )
        ),
        M3ECircularFolder(
            key = "student-affairs",
            name = "Student Affairs",
            documents = listOf(
                M3ECircularDocument("CIR-088", "Hostel room allotment list")
            )
        )
    ),
    expandedKeys = setOf("academic-2026", "student-affairs"),
    downloadStates = mapOf(
        "CIR-102" to M3ECircularDownloadUi(
            M3ECircularDownloadStatus.SAVED,
            "mid-semester-schedule.pdf"
        ),
        "CIR-103" to M3ECircularDownloadUi(
            M3ECircularDownloadStatus.FAILED,
            "Download failed — check your connection"
        ),
        "CIR-088" to M3ECircularDownloadUi(M3ECircularDownloadStatus.DOWNLOADING)
    )
)

@Preview
@Composable
private fun M3ECircularsScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECircularsScreen(state = previewCircularsState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ECircularsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECircularsScreen(state = previewCircularsState(), onAction = {})
    }
}
