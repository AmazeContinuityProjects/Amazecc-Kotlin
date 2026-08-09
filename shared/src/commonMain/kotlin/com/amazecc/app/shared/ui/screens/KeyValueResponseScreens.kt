package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.DataTableCard
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.KPICard
import kotlinx.coroutines.launch

@Composable
fun KeyValueResponseScreen(
    title: String,
    description: String,
    loadingText: String,
    load: suspend () -> ArrearResponse
) {
    val colors = AmazeTheme.colors
    var response by remember { mutableStateOf<ArrearResponse?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            response = load()
        } catch (e: Exception) {
            response = ArrearResponse(success = false, message = e.message, error = e.toString())
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                    Text(loadingText, style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
            ) {
                item { HeaderSpacer() }
                val res = response
                if (res == null || res.success == false) {
                    item {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Info, null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                                Text(res?.message ?: "No data available", color = colors.textSecondary)
                            }
                        }
                    }
                } else {
                    if (res.keyValuePairs.isNotEmpty()) {
                        item { KPICard(pairs = res.keyValuePairs, colors = colors) }
                    }
                    res.tables.forEach { table ->
                        item { DataTableCard(table = table, colors = colors) }
                    }
                    res.messages.forEach { msg ->
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
fun TabbedKeyValueScreen(
    title: String,
    description: String,
    loadingText: String,
    tabLabels: List<String>,
    endpointKeys: List<String>,
    load: suspend (String) -> ArrearResponse
) {
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
                    load(ep)
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
                    Text(loadingText, style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
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
                item { HeaderSpacer() }
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
