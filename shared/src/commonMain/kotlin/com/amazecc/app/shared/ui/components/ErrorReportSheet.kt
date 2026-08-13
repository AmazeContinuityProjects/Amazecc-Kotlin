package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import kotlin.math.roundToInt

/**
 * Non-dismissible bottom sheet showing download progress while the syllabus PDF is fetched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadProgressSheet(
    fileName: String,
    progress: Float
) {
    val colors = AmazeTheme.colors
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it == SheetValue.Expanded }
    )

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Downloading syllabus",
                style = AmazeTheme.typography.heading.copy(
                    fontSize = AmazeTheme.fontSize.xl,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                fileName,
                style = AmazeTheme.typography.body.copy(
                    fontSize = AmazeTheme.fontSize.md,
                    color = colors.textSecondary
                )
            )
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colors.accent,
                trackColor = colors.surface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorReportSheet(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    detail: String? = null,
    onReport: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = AmazeTheme.typography.heading.copy(
                    fontSize = AmazeTheme.fontSize.xl,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = AmazeTheme.typography.body.copy(
                    fontSize = AmazeTheme.fontSize.md,
                    color = colors.textSecondary
                )
            )
            if (detail != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    detail,
                    style = AmazeTheme.typography.caption.copy(
                        fontFamily = FontFamily.Monospace,
                        color = colors.textMuted
                    )
                )
            }
            Spacer(Modifier.height(24.dp))
            if (onReport != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmazeButton(
                        text = "Report",
                        onClick = onReport,
                        icon = Icons.Rounded.BugReport,
                        variant = ButtonVariant.DANGER,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Close",
                        onClick = onDismiss,
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                AmazeButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
