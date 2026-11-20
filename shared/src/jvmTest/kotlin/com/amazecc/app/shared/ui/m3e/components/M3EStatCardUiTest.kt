package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D-130: the stat tile keeps its label at the top and its value block at the
 * bottom of a caller-owned height, and the insight carousel no longer exposes
 * any pause/resume control.
 */
@OptIn(ExperimentalTestApi::class)
class M3EStatCardUiTest {

    @Test
    fun fixedHeightStatCardShowsLabelAndValue() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EStatCard(
                    stat = M3EStatItem(
                        id = "attendance",
                        label = "Attendance",
                        value = "94.2%",
                        sub = M3EStatSubtitle.fromData("Safe attendance standing"),
                        tone = M3EStatTone.GOOD,
                        icon = Icons.Rounded.School
                    ),
                    modifier = Modifier.height(136.dp)
                )
            }
        }

        // The label is uppercased by the card; the subtitle must stay data-backed.
        onNodeWithText("ATTENDANCE").assertExists()
        onNodeWithText("94.2%").assertExists()
        onNodeWithText("Safe attendance standing").assertExists()
        // D-130: title at the top, value block at the bottom of the 136dp card.
        val titleBottom = onNodeWithText("ATTENDANCE").fetchSemanticsNode().boundsInRoot.bottom
        val valueTop = onNodeWithText("94.2%").fetchSemanticsNode().boundsInRoot.top
        assertTrue(titleBottom < valueTop, "Title must sit above the highlighted value")
    }

    @Test
    fun carouselExposesDotsButNoPauseControl() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EInsightCarouselCard(
                    items = listOf(
                        M3EStatItem(
                            id = "cgpa",
                            label = "Cumulative GPA",
                            value = "9.18",
                            sub = M3EStatSubtitle.fromMetric("9.18", "CGPA"),
                            tone = M3EStatTone.PRIMARY,
                            icon = Icons.Rounded.School
                        ),
                        M3EStatItem(
                            id = "credits",
                            label = "Credits Earned",
                            value = "118 / 160",
                            sub = M3EStatSubtitle.fromData("118 of 160 credits earned"),
                            tone = M3EStatTone.GOOD
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp),
                    // Keep page 0 stable for the dot announcement assertion.
                    autoAdvanceDelayMillis = 60_000L
                )
            }
        }

        onNodeWithContentDescription("Slide 1 of 2").assertExists()
        onNodeWithContentDescription("Pause automatic slide rotation").assertDoesNotExist()
        onNodeWithContentDescription("Resume automatic slide rotation").assertDoesNotExist()
        // The carousel tile uses the same top-title / bottom-value anchoring.
        val titleBottom = onNodeWithText("CUMULATIVE GPA").fetchSemanticsNode().boundsInRoot.bottom
        val valueTop = onNodeWithText("9.18").fetchSemanticsNode().boundsInRoot.top
        assertTrue(titleBottom < valueTop, "Carousel title must sit above the highlighted value")
    }
}
