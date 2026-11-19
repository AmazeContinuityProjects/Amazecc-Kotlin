package com.amazecc.app.shared.ui.screens.m3e

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A document metadata row (label → value). */
@Immutable
data class M3EDocumentRow(
    val label: String,
    val value: String
)

/** One document in the list (e.g. Bonafide Certificate, E-Transcript, Additional Learning). */
@Immutable
data class M3EDocumentCard(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val details: List<M3EDocumentRow> = emptyList(),
    val messages: List<String> = emptyList(),
    val error: String? = null
)

/** Immutable, screen-owned state for [M3EDocumentsScreen]. */
@Immutable
data class M3EDocumentsState(
    val documents: List<M3EDocumentCard> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** User intents emitted by [M3EDocumentsScreen]. */
sealed interface M3EDocumentsAction {
    data object Refresh : M3EDocumentsAction
    /** Open/download a document identified by its circular id (when one is available). */
    data class OpenDocument(val id: String) : M3EDocumentsAction
}

/**
 * Material 3 Expressive Documents screen — a simple, flat list of documents.
 * No tabs and no per-endpoint scrapers: the host feeds [M3EDocumentCard]s from
 * `AppState.documents`, which flattens the existing circulars folder tree.
 * 100% stateless: receives [M3EDocumentsState] and emits [M3EDocumentsAction].
 */
@Composable
fun M3EDocumentsScreen(
    state: M3EDocumentsState,
    onAction: (M3EDocumentsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
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
                        text = "Documents",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Bonafide, transcripts & learning records",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onAction(M3EDocumentsAction.Refresh) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh documents",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (state.isLoading) {
            item(key = "progress") {
                M3EWavyLinearProgress(progress = 0.5f)
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

        items(state.documents, key = { it.id }) { document ->
            DocumentCard(
                document = document,
                onOpenDocument = { id -> onAction(M3EDocumentsAction.OpenDocument(id)) }
            )
        }

        if (!state.isLoading && state.documents.isEmpty() && state.errorMessage.isNullOrBlank()) {
            item(key = "empty") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No documents available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(BOTTOM_NAV_PADDING))
        }
    }
}

@Composable
private fun DocumentCard(
    document: M3EDocumentCard,
    onOpenDocument: (String) -> Unit
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.LOW
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    if (document.subtitle.isNotBlank()) {
                        Text(
                            text = document.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                // Optional affordance: only shown when the flattened circular carries an id.
                if (document.id.isNotBlank()) {
                    IconButton(onClick = { onOpenDocument(document.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Open ${document.title}",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            document.details.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            document.messages.forEach { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            if (!document.error.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = document.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error
                    )
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewDocumentsState() = M3EDocumentsState(
    documents = listOf(
        M3EDocumentCard(
            id = "bonafide",
            title = "Bonafide Certificate",
            subtitle = "Internship / passport purpose",
            details = listOf(
                M3EDocumentRow("Student", "Aravind Kumar"),
                M3EDocumentRow("Register No.", "21BCE1042")
            )
        ),
        M3EDocumentCard(
            id = "etranscript",
            title = "E-Transcript",
            subtitle = "Semester-wise grade record"
        ),
        M3EDocumentCard(
            id = "additional-learning",
            title = "Additional Learning",
            subtitle = "Certified electives"
        )
    )
)

@Preview
@Composable
private fun M3EDocumentsScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EDocumentsScreen(state = previewDocumentsState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EDocumentsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EDocumentsScreen(state = previewDocumentsState(), onAction = {})
    }
}
