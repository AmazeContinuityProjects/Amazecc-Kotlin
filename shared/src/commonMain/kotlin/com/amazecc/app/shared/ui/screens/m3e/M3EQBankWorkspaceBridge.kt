package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.QBankQuestion
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppBackStack
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private fun QBankQuestion.toItem() = M3EQBankQuestionItem(
    id = question_id,
    text = question_text,
    type = question_type,
    options = options,
    correctAnswer = correct_answer,
    marks = marks,
    topic = topic_name,
    examSemester = exam_semester
)

private fun SettingsManager.QuizTopicStat.toItem() = M3EQBankTopicStatItem(topic, attempts, correct, totalTimeSec)

private fun SettingsManager.QuizCourseStats.toItem() = M3EQBankCourseStatsItem(
    courseCode = courseCode,
    attempts = attempts,
    correct = correct,
    topics = topics.values.map { it.toItem() },
    missedIds = missedQuestionIds
)

private fun SettingsManager.SwotConfig.toItem() = M3EQBankSwotConfigItem(
    strengthMinAttempts = strengthMinAttempts,
    strengthAccuracy = strengthAccuracy,
    weaknessMinAttempts = weaknessMinAttempts,
    weaknessAccuracy = weaknessAccuracy
)

private fun M3EQBankSwotConfigItem.toSettings() = SettingsManager.SwotConfig(
    strengthMinAttempts = strengthMinAttempts,
    strengthAccuracy = strengthAccuracy,
    weaknessMinAttempts = weaknessMinAttempts,
    weaknessAccuracy = weaknessAccuracy
)

/**
 * Stateful host for the complete M3E QBank feature: catalog + per-course workspace.
 *
 * ALL side effects live here — AmazeClient (online), SettingsManager (offline practice
 * cache), navigation, URI opening and the quiz timer. The two stateless screens
 * ([M3EQBankScreen] and [M3EQBankWorkspaceScreen]) only render immutable state and emit actions.
 *
 * Online API note: there is no VtopDataSource offline scraper for QBank. Papers and
 * questions always come from AmazeClient; when a fetch fails we surface a clear
 * "offline — requires connection" state and keep the cached local practice stats visible.
 *
 * This host also consumes [AppState.qbankCourseTarget] so tapping a course (here or from
 * another screen such as Marks Predictor) actually opens the workspace.
 */
@Composable
fun M3EQBankWorkspaceHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val qbankCourseTarget by AppState.qbankCourseTarget.collectAsState()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    // ---- catalog ----
    var courses by remember { mutableStateOf<List<M3EQBankCourse>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogOffline by remember { mutableStateOf(false) }
    var isLoadingCourses by remember { mutableStateOf(true) }
    var catalogRefreshTick by remember { mutableStateOf(0) }
    var statsTick by remember { mutableStateOf(0) }

    // ---- selected course / workspace ----
    var selectedCourse by remember { mutableStateOf<M3EQBankCourse?>(null) }
    var wsRefreshTick by remember { mutableStateOf(0) }
    var wsLoading by remember { mutableStateOf(true) }
    var wsError by remember { mutableStateOf<String?>(null) }
    var wsOffline by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf<List<M3EQBankQuestionItem>>(emptyList()) }
    var papers by remember { mutableStateOf<List<M3EQBankPaperItem>>(emptyList()) }
    var wsTab by remember { mutableStateOf(M3EQBankWorkspaceTab.PRACTICE) }
    var wsView by remember { mutableStateOf(M3EQBankWorkspaceView.OVERVIEW) }
    var wsFilter by remember { mutableStateOf(M3EQBankPracticeFilter.ALL) }
    var activeIndex by remember { mutableStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<String, String>() }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    val flagged = remember { mutableStateMapOf<String, Boolean>() }
    val sessionResults = remember { mutableStateMapOf<String, Boolean?>() }
    val sessionTimeSpent = remember { mutableStateMapOf<String, Int>() }
    val questionStartedAt = remember { mutableStateMapOf<String, Long>() }
    var secondsLeft by remember { mutableStateOf(60) }
    var timerEnabled by remember { mutableStateOf(true) }
    var reviewQueue by remember { mutableStateOf<List<M3EQBankQuestionItem>>(emptyList()) }
    var showSummary by remember { mutableStateOf(false) }
    var showSwotConfig by remember { mutableStateOf(false) }
    var uploadOpen by remember { mutableStateOf(false) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadLink by remember { mutableStateOf("") }
    var uploadType by remember { mutableStateOf("CAT 1") }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var qbankStats by remember { mutableStateOf(SettingsManager.getQBankStats()) }
    var swotConfig by remember { mutableStateOf(SettingsManager.getSwotConfig()) }

    val code = selectedCourse?.courseCode.orEmpty()
    val wsStats = qbankStats[code]
    val queue = if (wsView == M3EQBankWorkspaceView.REVIEW) reviewQueue else questions
    val currentQuestion = queue.getOrNull(activeIndex)

    // ---- catalog stats (cached, offline-safe) ----
    val catalogStats = remember(qbankStats, statsTick) {
        val all = qbankStats.values
        val attempts = all.sumOf { it.attempts }
        val correct = all.sumOf { it.correct }
        if (attempts == 0) null
        else M3EQBankStats(attempts, correct, correct.toDouble() / attempts * 100.0)
    }

    // ---- catalog load ----
    LaunchedEffect(catalogRefreshTick) {
        isLoadingCourses = true
        catalogError = null
        catalogOffline = false
        try {
            val res = AmazeClient.getQBankCourses()
            if (res.success) {
                courses = res.courses.distinctBy { it.courseCode }.map { M3EQBankCourse(it.courseCode, it.courseTitle) }
            } else {
                catalogOffline = true
                catalogError = res.message ?: res.error ?: "Unable to load courses"
            }
        } catch (e: Exception) {
            catalogOffline = true
            catalogError = e.message ?: "Unable to load courses"
        }
        isLoadingCourses = false
    }

    // ---- consume cross-screen course target and open the workspace ----
    LaunchedEffect(qbankCourseTarget) {
        val target = qbankCourseTarget ?: return@LaunchedEffect
        if (selectedCourse?.courseCode?.equals(target, ignoreCase = true) != true) {
            selectedCourse = courses.firstOrNull { it.courseCode.equals(target, ignoreCase = true) }
                ?: M3EQBankCourse(target, "")
        }
        AppState.consumeQBankCourseTarget()
    }

    fun resetQuizState() {
        userAnswers.clear()
        revealed.clear()
        flagged.clear()
        sessionResults.clear()
        sessionTimeSpent.clear()
        questionStartedAt.clear()
        activeIndex = 0
        reviewQueue = emptyList()
        wsView = M3EQBankWorkspaceView.OVERVIEW
        wsFilter = M3EQBankPracticeFilter.ALL
        showSummary = false
    }

    // ---- workspace load (online questions/papers + cached stats) ----
    LaunchedEffect(selectedCourse?.courseCode, wsRefreshTick) {
        val courseCode = selectedCourse?.courseCode ?: return@LaunchedEffect
        wsLoading = true
        wsError = null
        wsOffline = false
        questions = emptyList()
        papers = emptyList()
        resetQuizState()
        qbankStats = SettingsManager.getQBankStats()
        swotConfig = SettingsManager.getSwotConfig()
        try {
            val qRes = AmazeClient.getQBankQuestions(courseCode)
            if (qRes.success) {
                questions = qRes.data.distinctBy { it.question_id }.map { it.toItem() }
            } else {
                wsOffline = true
                wsError = qRes.message ?: qRes.error ?: "Unable to load questions — requires a connection"
            }
            val pRes = AmazeClient.getQBankPapers(courseCode)
            if (pRes.success) {
                papers = pRes.data.map { M3EQBankPaperItem(it.title, it.link, it.type) }
            } else {
                wsOffline = true
                if (wsError == null) wsError = pRes.message ?: pRes.error ?: "Unable to load papers — requires a connection"
            }
        } catch (e: Exception) {
            wsOffline = true
            wsError = e.message ?: "Unable to load — requires a connection"
        }
        wsLoading = false
    }

    fun recordAnswer(q: M3EQBankQuestionItem, overwrite: Boolean = false) {
        if (!overwrite && sessionResults.containsKey(q.id)) return
        val startedAt = questionStartedAt[q.id] ?: Clock.System.now().toEpochMilliseconds()
        val spentSec = ((Clock.System.now().toEpochMilliseconds() - startedAt).coerceAtLeast(0L) / 1000L).toInt()
        sessionTimeSpent[q.id] = spentSec
        val correct = if (q.options.isNullOrEmpty()) null else qbankIsAnswerCorrect(q, userAnswers[q.id] ?: "")
        sessionResults[q.id] = correct
        SettingsManager.recordQBankAnswer(code, q.topic, correct, spentSec, q.id)
        qbankStats = SettingsManager.getQBankStats()
        statsTick++
    }

    fun launchReview() {
        val missed = (qbankStats[code]?.missedQuestionIds ?: emptyList()).toSet()
        val queue = questions.filter { missed.contains(it.id) }
        if (queue.isNotEmpty()) {
            reviewQueue = queue
            activeIndex = 0
            wsView = M3EQBankWorkspaceView.REVIEW
            wsFilter = M3EQBankPracticeFilter.ALL
            showSummary = false
            queue.forEach { q ->
                revealed.remove(q.id)
                userAnswers.remove(q.id)
                questionStartedAt.remove(q.id)
                sessionResults.remove(q.id)
            }
        }
    }

    // ---- quiz timer side effect (UI-local; no stale SettingsManager timer API) ----
    LaunchedEffect(currentQuestion?.id, revealed[currentQuestion?.id ?: ""], timerEnabled, wsView) {
        val q = currentQuestion ?: return@LaunchedEffect
        if (revealed[q.id] == true || !timerEnabled) return@LaunchedEffect
        secondsLeft = if (q.options.isNullOrEmpty()) 120 else 60
        while (secondsLeft > 0) {
            delay(1000)
            if (revealed[q.id] == true) break
            secondsLeft--
        }
        if (secondsLeft <= 0 && revealed[q.id] != true) {
            recordAnswer(q, overwrite = wsView == M3EQBankWorkspaceView.REVIEW)
            revealed[q.id] = true
        }
    }

    LaunchedEffect(currentQuestion?.id) {
        currentQuestion?.let { questionStartedAt[it.id] = Clock.System.now().toEpochMilliseconds() }
    }

    fun handleBack() {
        when {
            showSwotConfig -> showSwotConfig = false
            showSummary -> showSummary = false
            wsView != M3EQBankWorkspaceView.OVERVIEW && sessionResults.isNotEmpty() && !showSummary -> showSummary = true
            wsView != M3EQBankWorkspaceView.OVERVIEW -> {
                wsView = M3EQBankWorkspaceView.OVERVIEW
                reviewQueue = emptyList()
            }
            selectedCourse != null && AppBackStack.entries.value.dropLast(1).lastOrNull()?.screen == Screen.COURSE_DETAIL.name -> {
                AppState.navigateBack()
            }
            selectedCourse != null -> {
                selectedCourse = null
                qbankStats = SettingsManager.getQBankStats()
            }
            else -> if (!AppState.navigateBack()) AppState.navigateTo(Screen.MORE)
        }
    }

    AppBackHandler { handleBack() }

    val filteredCourses = remember(courses, searchQuery) {
        courses.filter {
            it.courseCode.contains(searchQuery, ignoreCase = true) ||
                it.courseTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        val course = selectedCourse?.let { selected ->
            courses.firstOrNull { it.courseCode.equals(selected.courseCode, ignoreCase = true) } ?: selected
        }
        if (course == null) {
            M3EQBankScreen(
                state = M3EQBankState(
                    courses = courses,
                    filteredCourses = filteredCourses,
                    searchQuery = searchQuery,
                    stats = catalogStats,
                    errorMessage = catalogError,
                    isLoading = isLoadingCourses,
                    isOffline = catalogOffline
                ),
                onAction = { action ->
                    when (action) {
                        is M3EQBankAction.SearchQueryChanged -> searchQuery = action.query
                        is M3EQBankAction.OpenCourse -> AppState.openQBankCourse(action.courseCode)
                        is M3EQBankAction.Refresh -> catalogRefreshTick++
                        is M3EQBankAction.Back -> if (!AppState.navigateBack()) AppState.navigateTo(Screen.MORE)
                    }
                },
                modifier = modifier
            )
        } else {
            M3EQBankWorkspaceScreen(
                state = M3EQBankWorkspaceState(
                    courseCode = code,
                    courseTitle = course.courseTitle,
                    isLoading = wsLoading,
                    errorMessage = wsError,
                    isOffline = wsOffline,
                    questions = questions,
                    papers = papers,
                    tab = wsTab,
                    view = wsView,
                    filter = wsFilter,
                    activeIndex = activeIndex,
                    userAnswers = userAnswers.toMap(),
                    revealed = revealed.toMap(),
                    flagged = flagged.toMap(),
                    sessionResults = sessionResults.toMap(),
                    secondsLeft = secondsLeft,
                    totalSeconds = if (currentQuestion?.options.isNullOrEmpty()) 120 else 60,
                    timerEnabled = timerEnabled,
                    stats = wsStats?.toItem(),
                    reviewQueue = reviewQueue,
                    showSummary = showSummary,
                    upload = M3EQBankUploadState(uploadOpen, uploadTitle, uploadLink, uploadType, uploadStatus, uploading),
                    swotConfig = swotConfig.toItem(),
                    showSwotConfig = showSwotConfig
                ),
                onAction = { action ->
                    when (action) {
                        M3EQBankWorkspaceAction.Back -> handleBack()
                        M3EQBankWorkspaceAction.Refresh -> wsRefreshTick++
                        is M3EQBankWorkspaceAction.SelectTab -> wsTab = action.tab
                        is M3EQBankWorkspaceAction.SelectFilter -> wsFilter = action.filter
                        is M3EQBankWorkspaceAction.OpenQuestion -> {
                            if (action.index in questions.indices) {
                                wsView = M3EQBankWorkspaceView.QUIZ
                                activeIndex = action.index
                            }
                        }
                        is M3EQBankWorkspaceAction.SetAnswer -> userAnswers[action.questionId] = action.value
                        M3EQBankWorkspaceAction.Reveal -> currentQuestion?.let { q ->
                            recordAnswer(q)
                            revealed[q.id] = true
                        }
                        M3EQBankWorkspaceAction.ToggleFlag -> currentQuestion?.let { q ->
                            flagged[q.id] = !(flagged[q.id] ?: false)
                        }
                        M3EQBankWorkspaceAction.ToggleTimer -> timerEnabled = !timerEnabled
                        M3EQBankWorkspaceAction.NextQuestion -> if (activeIndex < queue.size - 1) activeIndex++
                        M3EQBankWorkspaceAction.PreviousQuestion -> if (activeIndex > 0) activeIndex--
                        is M3EQBankWorkspaceAction.SelectQueueIndex -> if (action.index in queue.indices) activeIndex = action.index
                        M3EQBankWorkspaceAction.LaunchReview -> launchReview()
                        M3EQBankWorkspaceAction.DismissSummary -> showSummary = false
                        M3EQBankWorkspaceAction.FinishSession -> {
                            showSummary = false
                            wsView = M3EQBankWorkspaceView.OVERVIEW
                            reviewQueue = emptyList()
                        }
                        M3EQBankWorkspaceAction.ReviewMissed -> {
                            showSummary = false
                            launchReview()
                        }
                        M3EQBankWorkspaceAction.ToggleUploadForm -> uploadOpen = !uploadOpen
                        is M3EQBankWorkspaceAction.UpdateUploadTitle -> uploadTitle = action.value
                        is M3EQBankWorkspaceAction.UpdateUploadLink -> uploadLink = action.value
                        is M3EQBankWorkspaceAction.UpdateUploadType -> uploadType = action.value
                        M3EQBankWorkspaceAction.SubmitUpload -> {
                            uploading = true
                            uploadStatus = null
                            scope.launch {
                                try {
                                    val res = AmazeClient.postQBankPaper(code, uploadTitle.trim(), uploadLink.trim(), uploadType)
                                    val succeeded = res?.success == true
                                    uploadStatus = if (succeeded) "Paper uploaded!" else res?.message ?: "Upload failed"
                                    if (succeeded) {
                                        uploadOpen = false
                                        uploadTitle = ""
                                        uploadLink = ""
                                        try {
                                            val pRes = AmazeClient.getQBankPapers(code)
                                            if (pRes.success) papers = pRes.data.map { M3EQBankPaperItem(it.title, it.link, it.type) }
                                        } catch (_: Exception) { }
                                    }
                                } catch (e: Exception) {
                                    uploadStatus = "Error: ${e.message}"
                                }
                                uploading = false
                            }
                        }
                        is M3EQBankWorkspaceAction.OpenPaper -> {
                            try {
                                uriHandler.openUri(action.paper.link)
                            } catch (_: Exception) { }
                        }
                        M3EQBankWorkspaceAction.OpenSwotConfig -> showSwotConfig = true
                        M3EQBankWorkspaceAction.CloseSwotConfig -> showSwotConfig = false
                        is M3EQBankWorkspaceAction.SaveSwotConfig -> {
                            swotConfig = action.config.toSettings()
                            SettingsManager.saveSwotConfig(swotConfig)
                            showSwotConfig = false
                        }
                    }
                },
                modifier = modifier
            )
        }
    }
}
