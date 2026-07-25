package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun LatexViewer(latex: String, modifier: Modifier = Modifier)
