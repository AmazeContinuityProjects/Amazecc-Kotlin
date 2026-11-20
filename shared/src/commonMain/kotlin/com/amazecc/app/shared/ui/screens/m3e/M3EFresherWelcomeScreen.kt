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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Categories of the fresher orientation guide. */
enum class M3EFresherCategory { CAMPUS, ACADEMICS, HOSTEL, TRANSPORT, EVENTS }

/** One orientation section. */
@Immutable
data class M3EFresherSection(val category: M3EFresherCategory, val title: String, val description: String)

/** Immutable, stateless state for [M3EFresherWelcomeScreen]. */
@Immutable
data class M3EFresherWelcomeState(
    val sections: List<M3EFresherSection> = emptyList(),
    /** Real profile key used by [M3EFresherContent] to build [sections]. */
    val profile: M3EFresherProfile = M3EFresherProfile()
)

/** User intents emitted by [M3EFresherWelcomeScreen]. */
sealed interface M3EFresherWelcomeAction {
    data class OpenSection(val category: M3EFresherCategory) : M3EFresherWelcomeAction
    data object Back : M3EFresherWelcomeAction
}

/**
 * Material 3 Expressive Fresher Welcome / Orientation guide.
 * 100% stateless: accepts [M3EFresherWelcomeState], emits [M3EFresherWelcomeAction].
 */
@Composable
fun M3EFresherWelcomeScreen(
    state: M3EFresherWelcomeState,
    onAction: (M3EFresherWelcomeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero") {
                M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 28.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            M3EFresherContent.welcomeTitle(state.profile),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onSurface
                        )
                        Text(
                            M3EFresherContent.welcomeSubtitle(state.sections),
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            items(state.sections, key = { it.category.name }) { section ->
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.STANDARD,
                    onClick = { onAction(M3EFresherWelcomeAction.OpenSection(section.category)) }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(sectionTint(section.category).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(sectionIcon(section.category), contentDescription = null, tint = sectionTint(section.category), modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(section.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(section.description, fontSize = 13.sp, color = colors.onSurfaceVariant, lineHeight = 20.sp)
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }
}

@Composable
private fun sectionIcon(category: M3EFresherCategory): ImageVector = when (category) {
    M3EFresherCategory.CAMPUS -> Icons.Rounded.School
    M3EFresherCategory.ACADEMICS -> Icons.AutoMirrored.Rounded.MenuBook
    M3EFresherCategory.HOSTEL -> Icons.Rounded.Apartment
    M3EFresherCategory.TRANSPORT -> Icons.Rounded.DirectionsBus
    M3EFresherCategory.EVENTS -> Icons.Rounded.Celebration
}

@Composable
private fun sectionTint(category: M3EFresherCategory) = when (category) {
    M3EFresherCategory.CAMPUS -> M3ETheme.colors.primary
    M3EFresherCategory.ACADEMICS -> M3ETheme.colors.success
    M3EFresherCategory.HOSTEL -> M3ETheme.colors.warning
    M3EFresherCategory.TRANSPORT -> M3ETheme.colors.error
    M3EFresherCategory.EVENTS -> M3ETheme.colors.secondary
}

/** Preview state comes from the same real content provider the host will use. */
private fun previewFresherState() = M3EFresherWelcomeState(
    profile = M3EFresherProfile(displayName = "Student", isHosteller = false, isDayScholar = false),
    sections = M3EFresherContent.sectionsFor(
        M3EFresherProfile(displayName = "Student", isHosteller = false, isDayScholar = false)
    )
)

@Preview
@Composable
private fun M3EFresherWelcomeScreenPreview() {
    AmazeM3ETheme(isDark = false) { M3EFresherWelcomeScreen(state = previewFresherState(), onAction = {}) }
}

@Preview
@Composable
private fun M3EFresherWelcomeScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) { M3EFresherWelcomeScreen(state = previewFresherState(), onAction = {}) }
}
