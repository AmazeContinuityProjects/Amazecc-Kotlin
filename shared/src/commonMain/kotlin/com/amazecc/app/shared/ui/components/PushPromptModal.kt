package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushPromptModal(
    onEnable: () -> Unit,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.info.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = colors.info,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Never Miss a Class!",
                        style = AmazeTheme.typography.heading.copy(
                            fontSize = AmazeTheme.fontSize.xl,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }
            }

            Text(
                "AmazeCC can send you push notifications for your weekly VITOL classes directly to this device.",
                style = AmazeTheme.typography.body.copy(
                    fontSize = AmazeTheme.fontSize.lg,
                    color = colors.textSecondary
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmazeButton(
                    text = "Maybe Later",
                    onClick = onDismiss,
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AmazeButton(
                    text = "Enable Alerts",
                    onClick = onEnable,
                    variant = ButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
