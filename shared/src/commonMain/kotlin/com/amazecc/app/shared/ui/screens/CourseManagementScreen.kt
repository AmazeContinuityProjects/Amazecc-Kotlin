package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.ApiTable
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import kotlinx.coroutines.launch

private val endpointKeys = listOf("course-option-change", "exc-registration", "minor-honour", "course-completion")
private val tabLabels = listOf("Course Option", "EXC Registration", "Minor/Honour", "Completion")

@Composable
fun CourseManagementScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var dataMap by remember { mutableStateOf<Map<String, ArrearResponse>>(emptyMap()) }

    fun loadAll() {
        loading = true
        scope.launch {
            val map = mutableMapOf<String, ArrearResponse>()
            for (ep in endpointKeys) {
                map[ep] = try {
                    when (ep) {
                        "course-option-change" -> AmazeClient.getCourseOptionChange()
                        "exc-registration" -> AmazeClient.getExcRegistration()
                        "minor-honour" -> AmazeClient.getMinorHonour()
                        "course-completion" -> AmazeClient.getCourseCompletion()
                        else -> ArrearResponse(success = false, message = "Unknown")
                    }
                } catch (e: Exception) {
                    ArrearResponse(success = false, message = e.message, error = e.toString())
                }
            }
            dataMap = map
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Course Management",
            description = "Option changes, EXC, minors",
            showBackButton = true,
            showSyncButton = false
        )

        TabRow(
            selectedTabIndex = activeTab,
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = colors.accent
                )
            }
        ) {
            tabLabels.forEachIndexed { idx, label ->
                Tab(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    text = {
                        Text(label, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold))
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    Text("Loading course data...", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                }
            }
        } else {
            val currentEp = endpointKeys.getOrNull(activeTab)
            val response = currentEp?.let { dataMap[it] }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
            ) {
                if (response == null || response.success == false) {
                    item {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Info, null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                                Text(response?.message ?: "No data available", color = colors.textSecondary)
                            }
                        }
                    }
                } else {
                    if (response.keyValuePairs.isNotEmpty()) {
                        item { KPICard(pairs = response.keyValuePairs, colors = colors) }
                    }
                    response.tables.forEach { table ->
                        item { DataTableCard(table = table, colors = colors) }
                    }
                    response.messages.forEach { msg ->
                        item {
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Text(msg.message, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(AmazeTheme.spacing.md)) }
            }
        }
    }
}

@Composable
private fun KPICard(
    pairs: List<com.amazecc.app.shared.model.KeyValuePair>,
    colors: com.amazecc.app.shared.theme.AmazeColors
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
private fun DataTableCard(
    table: ApiTable,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (table.title != null) {
                Text(
                    table.title,
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
                            fontSize = 10.sp
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
                                fontSize = 12.sp
                            ),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
