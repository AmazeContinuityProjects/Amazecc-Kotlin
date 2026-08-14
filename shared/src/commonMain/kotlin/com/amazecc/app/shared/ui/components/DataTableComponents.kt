package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.ApiTable
import com.amazecc.app.shared.model.KeyValuePair
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme

@Composable
fun KPICard(
    pairs: List<KeyValuePair>,
    colors: AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pairs.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(pair.label, style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    Text(pair.value, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
            }
        }
    }
}

@Composable
fun DataTableCard(
    table: ApiTable,
    colors: AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tableTitle = table.title ?: table.caption
            if (tableTitle != null) {
                Text(
                    tableTitle,
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            }
            Row(modifier = Modifier
                .fillMaxWidth()
                .background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(AmazeTheme.radius.xs))
                .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                table.headers.forEachIndexed { idx, header ->
                    Text(
                        header,
                        modifier = Modifier.weight(1f),
                        style = AmazeTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.micro
                        )
                    )
                }
            }
            table.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    row.forEachIndexed { idx, cell ->
                        Text(
                            cell,
                            modifier = Modifier.weight(1f),
                            style = AmazeTheme.typography.body.copy(
                                color = colors.textPrimary,
                                fontSize = AmazeTheme.fontSize.sm
                            ),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
