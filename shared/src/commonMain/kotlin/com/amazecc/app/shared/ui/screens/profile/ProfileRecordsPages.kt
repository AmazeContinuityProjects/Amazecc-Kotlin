package com.amazecc.app.shared.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.KeyValueRow
import com.amazecc.app.shared.model.VtopTable
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupLabel

@Composable
fun EptSchedulePage() {
    val identity by UserStore.identity.collectAsState()
    val tables = identity.eptTables

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("EPT Schedule")
        if (tables.isEmpty()) {
            EmptyStateCard("No EPT scheduled")
        } else {
            tables.forEach { table ->
                VtopTableCard(table = table, title = null)
            }
        }
    }
}

@Composable
fun RegistrationSchedulePage() {
    val identity by UserStore.identity.collectAsState()
    val fields = identity.registrationFields
    val tables = identity.registrationTables

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Registration Schedule")
        if (tables.isEmpty() && fields.isEmpty()) {
            EmptyStateCard("No registration schedule available")
        } else {
            fields.takeIf { it.isNotEmpty() }?.let {
                KeyValueCard(title = null, rows = it)
            }
            tables.forEach { table ->
                VtopTableCard(table = table, title = null)
            }
        }
    }
}

@Composable
fun UniversityDayPage() {
    val identity by UserStore.identity.collectAsState()
    val title = identity.universityDayTitle
    val fields = identity.universityDayFields
    val tables = identity.universityDayTables

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("University Day")
        if (!title.isNullOrBlank()) {
            Text(
                title,
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = AmazeTheme.colors.textPrimary)
            )
        }
        if (tables.isEmpty() && fields.isEmpty()) {
            EmptyStateCard("No University Day details available")
        } else {
            fields.takeIf { it.isNotEmpty() }?.let {
                KeyValueCard(title = null, rows = it)
            }
            tables.forEach { table ->
                VtopTableCard(table = table, title = null)
            }
        }
    }
}

@Composable
fun DayboarderPage() {
    val identity by UserStore.identity.collectAsState()
    val dayboarder = identity.dayboarder

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Dayboarder Details")
        val rows = dayboarder?.fields.orEmpty()
        if (dayboarder == null || rows.isEmpty()) {
            EmptyStateCard("No dayboarder details available")
        } else {
            KeyValueCard(title = null, rows = rows)
        }
    }
}

@Composable
fun ApaarIdPage() {
    val identity by UserStore.identity.collectAsState()
    val apaar = identity.apaar

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("APAAR ID")
        if (apaar?.hasApaar != true) {
            EmptyStateCard("APAAR ID not generated yet")
        } else {
            apaar.fields.takeIf { it.isNotEmpty() }?.let {
                KeyValueCard(title = "Details", rows = it)
            }
            apaar.tables.forEach { table ->
                VtopTableCard(table = table, title = null)
            }
        }
    }
}

@Composable
private fun KeyValueCard(title: String?, rows: List<KeyValueRow>) {
    val colors = AmazeTheme.colors
    Column {
        if (title != null) {
            SettingsGroupLabel(title)
        }
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                rows.forEach { row ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(row.label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
                        Text(row.value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

@Composable
private fun VtopTableCard(table: VtopTable, title: String?) {
    val colors = AmazeTheme.colors
    Column {
        if (title != null) {
            SettingsGroupLabel(title)
        }
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                table.caption?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.uppercase(),
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                }
                if (table.headers.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth()) {
                        table.headers.forEach { header ->
                            Text(
                                header,
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AmazeTheme.fontSize.micro
                                ),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                }
                table.rows.forEachIndexed { _, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        val rowToShow = if (table.headers.isNotEmpty()) row else listOf(row.joinToString("  "))
                        rowToShow.forEach { cell ->
                            Text(
                                cell,
                                style = AmazeTheme.typography.body.copy(
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = AmazeTheme.fontSize.base
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}