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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.EmbeddedCourseSemantics
import com.amazecc.app.shared.domain.EmbeddedEntry
import com.amazecc.app.shared.domain.EmbeddedHalf
import com.amazecc.app.shared.domain.EmbeddedLinkCode
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.domain.CREDITS_PER_SEMESTER
import com.amazecc.app.shared.domain.buildCategoryRows
import com.amazecc.app.shared.domain.completedCourseCodes
import com.amazecc.app.shared.domain.courseStatus
import com.amazecc.app.shared.domain.curriculumNum
import com.amazecc.app.shared.domain.curriculumTotals
import com.amazecc.app.shared.domain.earnedCreditsFromGrades
import com.amazecc.app.shared.domain.isSpecialBasket
import com.amazecc.app.shared.domain.ongoingCourseCodes
import com.amazecc.app.shared.domain.ongoingCreditsByCategory
import com.amazecc.app.shared.domain.totalOngoingCredits
import com.amazecc.app.shared.model.CurriculumRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.CourseCompletionStatus
import com.amazecc.app.shared.domain.overhaul.CurriculumBasket
import com.amazecc.app.shared.domain.overhaul.M3EBasketProgress
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumCategory
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumAction
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumCourse
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Degree Curriculum Tracker.
 * 100% Stateless: accepts [M3ECurriculumState] and emits [M3ECurriculumAction].
 * Features Degree Completion Hero Card, Basket breakdown chips, and Status trackers.
 */
@Composable
fun M3ECurriculumScreen(
    state: M3ECurriculumState,
    onAction: (M3ECurriculumAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item(key = "curriculum_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Degree Curriculum",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = state.programmeName,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3ECurriculumAction.DownloadCurriculum) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download Curriculum",
                            tint = colors.onPrimaryContainer
                        )
                    }
                }
            }

            // Degree Completion Hero Card
            item(key = "hero_progress_card") {
                val completionPct = if (state.totalCreditsRequired > 0) {
                    (state.totalCreditsCompleted.toDouble() / state.totalCreditsRequired.toDouble() * 100.0)
                } else 0.0

                val remainingCredits = (state.totalCreditsRequired - state.totalCreditsCompleted).coerceAtLeast(0)

                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.HIGH,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Degree Completion",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                text = completionPct.toFixed(2, "%"),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.primary
                            )
                        }

                        M3EWavyLinearProgress(
                            progress = (completionPct / 100.0).toFloat(),
                            label = "Credit completion",
                            activeColor = colors.primary,
                            trackColor = colors.surfaceContainerHighest
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.totalCreditsCompleted} / ${state.totalCreditsRequired} Credits Completed",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "$remainingCredits Credits Remaining",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingCredits == 0) colors.success else colors.primary
                            )
                        }
                        state.expectedLabel?.let { label ->
                            Text(
                                text = "At $CREDITS_PER_SEMESTER cr / semester: $label",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Transient result of the last save; only composed when the host has one.
            state.downloadStatus?.let { status ->
                item(key = "download_status") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = status, fontSize = 12.sp, color = colors.onSurface)
                    }
                }
            }

            // Basket Breakdown Mini Cards
            item(key = "basket_breakdown") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.baskets) { basket ->
                        val pct = if (basket.creditsRequired > 0) {
                            (basket.creditsCompleted.toDouble() / basket.creditsRequired.toDouble() * 100.0).coerceAtMost(100.0)
                        } else 0.0

                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            cornerRadius = 16.dp,
                            modifier = Modifier.width(150.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = basket.basket.code,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                                Text(
                                    text = basket.basket.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${basket.creditsCompleted}/${basket.creditsRequired} cr (${pct.toFixed(2)}%)",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Real category rows from buildCategoryRows, including the elective/special baskets
            // that the four coarse chips cannot represent (CurriculumPage.tsx:816-939).
            if (state.categoryRows.isNotEmpty()) {
                item(key = "category_rows") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Credit categories",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        state.categoryRows.forEach { category ->
                            M3ESurfaceCard(
                                containerLevel = SurfaceContainerLevel.LOWEST,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = category.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2
                                        )
                                        if (!category.code.isNullOrBlank()) {
                                            Text(
                                                text = category.code,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.primary
                                            )
                                        }
                                        if (category.isSpecial) {
                                            Text(
                                                text = "Extra",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.secondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${category.earned.toFixed(1)} / ${category.required.toFixed(1)} cr" +
                                            if (category.ongoing > 0f) " · ${category.ongoing.toFixed(0)} cr live" else "",
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                    val pct = if (category.required > 0f) {
                                        (category.earned / category.required).coerceIn(0f, 1f)
                                    } else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.surfaceContainerHighest)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(pct)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Basket Filter Switcher
            item(key = "basket_filter") {
                M3EConnectedButtonGroup(
                    items = CurriculumBasket.entries,
                    selectedItem = state.selectedBasket,
                    onItemSelected = { onAction(M3ECurriculumAction.SelectBasket(it)) },
                    labelProvider = { it.label }
                )
            }

            // Search Bar
            item(key = "search_field") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3ECurriculumAction.UpdateSearch(it)) },
                    placeholder = { Text("Search course code or title...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = colors.surfaceContainerLow,
                        unfocusedContainerColor = colors.surfaceContainerLow,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    ),
                    singleLine = true
                )
            }

            // Courses List — embedded halves share one section header; every half keeps its card.
            val courseSections = buildCurriculumCourseSections(state.filteredCourses)
            if (courseSections.isEmpty()) {
                item(key = "empty_courses") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No courses match the criteria",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            } else {
                items(items = courseSections, key = { it.key }) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (section.label != null) {
                            // Web-exact label + the pair's summed credits, shown ONCE for the group.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = section.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${section.totalCredits} Cr total",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface,
                                )
                            }
                            Text(
                                text = section.baseCode,
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant,
                            )
                            section.combinedGrade?.let { grade ->
                                Text(
                                    text = "Combined grade $grade",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.success,
                                )
                            }
                        }
                        section.courses.forEach { course ->
                            M3ECurriculumCourseCard(
                                course = course,
                                onDownloadSyllabus = { code -> onAction(M3ECurriculumAction.DownloadSyllabus(code)) }
                            )
                        }
                    }
                }
            }

            // Bottom Spacing (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

/**
 * Maps VTOP's real category codes onto the four coarse filter chips. The snapshot uses
 * NC/UCC/OEC/PCC/CON (curriculum.html:288,325,362,399,436); the legacy PC/PE/UC/UE codes are
 * kept so older cached payloads still filter. NC is a non-credit category, so it stays in
 * "All" rather than claiming a progress card it cannot fill.
 */
internal fun curriculumBasketFor(code: String?, title: String): CurriculumBasket {
    val c = code?.trim()?.uppercase().orEmpty()
    val t = title.uppercase()
    return when {
        c == "PCC" || c == "PC" || t.contains("PROGRAMME CORE") || t.contains("PROGRAM CORE") ->
            CurriculumBasket.PROGRAM_CORE
        c == "UCC" || c == "UC" || t.contains("UNIVERSITY CORE") ->
            CurriculumBasket.UNIVERSITY_CORE
        c == "OEC" || c == "UE" || t.contains("OPEN ELECTIVE") || t.contains("UNIVERSITY ELECTIVE") ->
            CurriculumBasket.UNIVERSITY_ELECTIVE
        c == "CON" || c == "PE" || t.contains("CONCENTRATION") ||
            t.contains("PROGRAMME ELECTIVE") || t.contains("PROGRAM ELECTIVE") ->
            CurriculumBasket.PROGRAM_ELECTIVE
        else -> CurriculumBasket.ALL
    }
}

/**
 * Pure, headless counterpart of the curriculum host. Every number comes from the domain
 * curriculum port or the real grade/attendance snapshot — no placeholder programme, credit
 * totals, basket progress, status or grade is manufactured here.
 */
internal fun buildCurriculumState(
    curriculumRes: CurriculumRes?,
    snapshot: DomainSnapshot,
    publishedCreditsEarned: String? = null,
    programmeName: String? = null,
    selectedBasket: CurriculumBasket = CurriculumBasket.ALL,
    searchQuery: String = "",
    isLoading: Boolean = false,
    downloadStatus: String? = null,
): M3ECurriculumState {
    val attendance = Projections.currentSemesterAttendance(snapshot)
    val completed = completedCourseCodes(snapshot)
    val ongoing = ongoingCourseCodes(attendance)
    val earned = earnedCreditsFromGrades(snapshot)
        .takeIf { it > 0f } ?: curriculumNum(publishedCreditsEarned)
    val totals = curriculumTotals(curriculumRes, earned, totalOngoingCredits(attendance))
    val categoryMeta = curriculumRes?.categories.orEmpty().associateBy { it.code.trim().uppercase() }

    /**
     * The published grade and whether it came from an EXACT-code row, keyed by the canonical
     * [EmbeddedLinkCode.baseCode].
     *
     * An exact match wins, so two halves with different published letters keep their own. When a
     * half has its own row but no letter, it gets none — it must not borrow the other half's letter.
     * The base-code fallback is only for a basket item whose spelling has no exact row at all (e.g.
     * `BACSE106` against a stored `BACSE106(T)`); the flag makes sure such a borrowed letter can
     * never be published as a "shared" combined grade.
     */
    fun gradeFor(code: String): Pair<String?, Boolean> {
        val trimmed = code.trim()
        val rowsOnThisCode = snapshot.academics.semesters.values.asSequence()
            .flatMap { it.courses.values.asSequence() }
            .filter { it.code.trim() == trimmed }
            .toList()
        if (rowsOnThisCode.isNotEmpty()) {
            val exact = rowsOnThisCode.firstNotNullOfOrNull { it.grade?.letter?.takeIf { l -> l.isNotBlank() } }
            return exact to true
        }
        val base = EmbeddedLinkCode.baseCode(code)
        if (base.isEmpty()) return null to false
        val borrowed = snapshot.academics.semesters.values.asSequence()
            .flatMap { it.courses.values.asSequence() }
            .filter { EmbeddedLinkCode.baseCode(it.code) == base }
            .firstNotNullOfOrNull { it.grade?.letter?.takeIf { l -> l.isNotBlank() } }
        return borrowed to false
    }

    val courses = curriculumRes?.details.orEmpty().flatMap { detail ->
        val meta = categoryMeta[detail.code.trim().uppercase()]
        val code = detail.code.ifBlank { meta?.code }.orEmpty()
        val title = detail.name.ifBlank { meta?.name }.orEmpty()
        val basket = curriculumBasketFor(code, title)
        detail.baskets.flatMap { b ->
            b.items.map { item ->
                // Only a genuinely embedded label may claim an embedded section. D-187's labelFor
                // also classifies "Theory Only"/"Lab Only"; those are standalone, never embedded.
                // All embedded labels start with "Embedded" — never guess one from a bare code.
                val embeddedLabel = EmbeddedCourseSemantics.labelFor(item.type)
                    .takeIf { it.startsWith("Embedded") }
                val base = EmbeddedLinkCode.baseCode(item.code)
                // The completed/ongoing sets hold canonical base codes, so every basket item —
                // embedded or standalone — must match on that base. Otherwise a standalone
                // `CSE101L` (stored as `CSE101`) reads as NOT_STARTED against its raw code.
                val statusCode = base.ifBlank { item.code }
                val status = when (courseStatus(statusCode, completed, ongoing)) {
                    com.amazecc.app.shared.domain.CourseStatus.COMPLETED -> CourseCompletionStatus.COMPLETED
                    com.amazecc.app.shared.domain.CourseStatus.IN_PROGRESS -> CourseCompletionStatus.IN_PROGRESS
                    com.amazecc.app.shared.domain.CourseStatus.REMAINING -> CourseCompletionStatus.NOT_STARTED
                }
                val (publishedGrade, fromExactRow) = gradeFor(item.code)
                val shownGrade = publishedGrade?.takeIf { status == CourseCompletionStatus.COMPLETED }
                M3ECurriculumCourse(
                    courseCode = item.code,
                    courseTitle = item.name,
                    credits = item.credits,
                    basket = basket,
                    courseType = item.type ?: "Theory",
                    status = status,
                    grade = shownGrade,
                    gradeFromExactRow = shownGrade != null && fromExactRow,
                    embeddedLabel = embeddedLabel,
                    baseCode = base,
                )
            }
        }
    }

    val rows = buildCategoryRows(
        categories = curriculumRes?.categories.orEmpty(),
        details = curriculumRes?.details.orEmpty(),
        ongoingByCategory = ongoingCreditsByCategory(attendance),
    )
    val categoryRows = rows.map { row ->
        M3ECurriculumCategory(
            key = row.key,
            code = row.code,
            title = row.title,
            earned = row.earned,
            required = row.required,
            ongoing = row.ongoing,
            isSpecial = isSpecialBasket(row.title),
            courseCount = row.baskets.sumOf { it.items.size },
        )
    }
    val baskets = rows.mapNotNull { row ->
        val basket = curriculumBasketFor(row.code, row.title)
        if (basket == CurriculumBasket.ALL) null else M3EBasketProgress(
            basket = basket,
            creditsCompleted = kotlin.math.round(row.earned).toInt(),
            creditsRequired = kotlin.math.round(row.required).toInt(),
        )
    }.distinctBy { it.basket }

    val filtered = courses.filter { course ->
        val matchesBasket = selectedBasket == CurriculumBasket.ALL || course.basket == selectedBasket
        val matchesQuery = searchQuery.isBlank() ||
            course.courseCode.contains(searchQuery, ignoreCase = true) ||
            course.courseTitle.contains(searchQuery, ignoreCase = true)
        matchesBasket && matchesQuery
    }

    return M3ECurriculumState(
        programmeName = programmeName?.takeIf { it.isNotBlank() } ?: "Programme",
        totalCreditsCompleted = kotlin.math.round(totals.earned).toInt(),
        totalCreditsRequired = kotlin.math.round(totals.required).toInt(),
        baskets = baskets,
        selectedBasket = selectedBasket,
        courses = courses,
        filteredCourses = filtered,
        searchQuery = searchQuery,
        isLoading = isLoading,
        categoryRows = categoryRows,
        expectedLabel = totals.expectedLabel,
        downloadStatus = downloadStatus,
    )
}

/**
 * One rendered row of the course list: a plain course card, or an embedded pair's two cards under
 * one section header. The header carries the pair's web-exact label and its summed credits **once**;
 * each half keeps its own card, its own grade badge and its own syllabus action.
 */
internal data class CurriculumCourseSection(
    val key: String,
    val label: String?,
    val baseCode: String,
    val courses: List<M3ECurriculumCourse>,
    val totalCredits: Int,
    /** Only the two halves' published shared letter — never a letter inferred from a blend. */
    val combinedGrade: String?,
)

/**
 * Groups embedded halves by [M3ECurriculumCourse.baseCode], preserving the payload's order.
 *
 * Non-embedded courses (no classified label) stay one-card rows. A labelled pair is folded into a
 * single section, so the basket's two halves cannot render as two independent "5 Cr" rows. The
 * combined grade comes straight from [EmbeddedCourseSemantics.group], which only publishes it when
 * both halves carry the same real letter; no blended marks are used for a letter here.
 */
internal fun buildCurriculumCourseSections(
    courses: List<M3ECurriculumCourse>,
): List<CurriculumCourseSection> {
    if (courses.isEmpty()) return emptyList()
    val sections = mutableListOf<CurriculumCourseSection>()
    val embeddedBases = HashSet<String>()

    courses.forEachIndexed { index, course ->
        if (course.embeddedLabel == null || course.baseCode.isBlank()) {
            sections += CurriculumCourseSection(
                key = "course_${course.courseCode}_${course.basket.name}_$index",
                label = course.embeddedLabel,
                baseCode = course.baseCode,
                courses = listOf(course),
                totalCredits = course.credits,
                combinedGrade = null,
            )
            return@forEachIndexed
        }
        if (!embeddedBases.add(course.baseCode)) return@forEachIndexed

        val halves = courses.filter { it.embeddedLabel != null && it.baseCode == course.baseCode }
        val label = when {
            halves.any { it.embeddedLabel == EmbeddedCourseSemantics.LABEL_COMBINED } ->
                EmbeddedCourseSemantics.LABEL_COMBINED
            else -> halves.mapNotNull { it.embeddedLabel }.distinct().singleOrNull()
                ?: EmbeddedCourseSemantics.LABEL_COMBINED
        }
        val classified = halves.map { half ->
            val side = when (EmbeddedLinkCode.halfOf(half.courseCode, half.courseType)) {
                EmbeddedLinkCode.Half.LAB -> EmbeddedHalf.LAB
                else -> EmbeddedHalf.THEORY
            }
            half to EmbeddedEntry(
                code = half.courseCode,
                half = side,
                credits = half.credits.toDouble(),
                // A base-code fallback letter is not a published half letter: it must never be
                // reconciled into a "shared" combined grade.
                grade = half.grade.takeIf { half.gradeFromExactRow },
                title = half.courseTitle,
            )
        }
        val group = EmbeddedCourseSemantics.group(classified.map { it.second }).firstOrNull()
        // The group's gradeEntries apply D-187's canonical "keep first" rule per half. Only those
        // halves are rendered and the total comes from the group, so a duplicate report of one half
        // is neither shown twice nor counted twice (even when both reports share the same code).
        val canonical = group?.gradeEntries.orEmpty()
        val usedSides = HashSet<EmbeddedHalf>()
        val canonicalHalves = if (canonical.isEmpty()) {
            halves
        } else {
            classified.mapNotNull { (course, entry) ->
                course.takeIf {
                    canonical.any { c -> c.code == entry.code && c.half == entry.half } &&
                        usedSides.add(entry.half)
                }
            }
        }
        sections += CurriculumCourseSection(
            key = "embedded_${course.baseCode}",
            label = label,
            baseCode = course.baseCode,
            courses = canonicalHalves,
            totalCredits = group?.totalCredits?.toInt() ?: 0,
            combinedGrade = group?.combinedGrade,
        )
    }
    return sections
}

@Composable
private fun M3ECurriculumCourseCard(
    course: M3ECurriculumCourse,
    onDownloadSyllabus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Course Code + Basket + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = course.courseCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${course.credits} Cr • ${course.basket.code}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                when (course.status) {
                    CourseCompletionStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.successContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.success,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = course.grade?.let { "Grade $it" } ?: "Completed",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.success
                                )
                            }
                        }
                    }
                    CourseCompletionStatus.IN_PROGRESS -> {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassEmpty,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "In Progress",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                            }
                        }
                    }
                    CourseCompletionStatus.NOT_STARTED -> {
                        Text(
                            text = "Yet to Complete",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Web-exact embedded label, shown only when the payload actually classified this row.
            // It is a label, not a control: the syllabus button below stays the row's only action.
            course.embeddedLabel?.let { label ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                    )
                }
            }

            // Title
            Text(
                text = course.courseTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            // Course Type
            Text(
                text = course.courseType,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant
            )

            // Per-course syllabus, the web page's per-item SyllabusButton (CurriculumPage.tsx:796).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = { onDownloadSyllabus(course.courseCode) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerHighest)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Download ${course.courseCode} syllabus",
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun createMockCurriculumState() = M3ECurriculumState(
    programmeName = "B.Tech Computer Science & Engineering (2021-25)",
    totalCreditsCompleted = 124,
    totalCreditsRequired = 160,
    baskets = listOf(
        M3EBasketProgress(CurriculumBasket.PROGRAM_CORE, 54, 58),
        M3EBasketProgress(CurriculumBasket.PROGRAM_ELECTIVE, 24, 36),
        M3EBasketProgress(CurriculumBasket.UNIVERSITY_CORE, 36, 42),
        M3EBasketProgress(CurriculumBasket.UNIVERSITY_ELECTIVE, 10, 24)
    ),
    courses = listOf(
        M3ECurriculumCourse(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_CORE,
            courseType = "Embedded Theory & Lab",
            status = CourseCompletionStatus.COMPLETED,
            grade = "S"
        ),
        M3ECurriculumCourse(
            courseCode = "CSE4001",
            courseTitle = "Parallel and Distributed Computing",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_ELECTIVE,
            courseType = "Theory Only",
            status = CourseCompletionStatus.IN_PROGRESS
        )
    ),
    filteredCourses = listOf(
        M3ECurriculumCourse(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_CORE,
            courseType = "Embedded Theory & Lab",
            status = CourseCompletionStatus.COMPLETED,
            grade = "S"
        ),
        M3ECurriculumCourse(
            courseCode = "CSE4001",
            courseTitle = "Parallel and Distributed Computing",
            credits = 4,
            basket = CurriculumBasket.PROGRAM_ELECTIVE,
            courseType = "Theory Only",
            status = CourseCompletionStatus.IN_PROGRESS
        )
    )
)

@Preview
@Composable
private fun M3ECurriculumScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECurriculumScreen(state = createMockCurriculumState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ECurriculumScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECurriculumScreen(state = createMockCurriculumState(), onAction = {})
    }
}
