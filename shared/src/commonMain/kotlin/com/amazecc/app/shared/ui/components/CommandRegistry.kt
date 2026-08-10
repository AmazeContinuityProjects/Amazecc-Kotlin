package com.amazecc.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.data.FfcsReportData
import com.amazecc.app.shared.ffcs.FfcsCourseProcessor
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.screens.more.appLibraryItems
import com.amazecc.app.shared.ui.screens.more.executeAppLibraryItem
import com.amazecc.app.shared.ui.screens.settings.SettingsSubScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The global search handler. Every search surface — navigation, settings,
 * modules, dynamic content (courses, buses, rooms, faculty) and tools —
 * resolves into command entries here. Large or network-backed data sets get
 * a [SubSearchSpec] entry that performs its own search inside the palette.
 */
@Composable
fun rememberGlobalCommands(): List<CommandItem> {
    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val tasks by AppState.tasks.collectAsState()
    val profile by AppState.studentProfile.collectAsState()
    val busesRes by AppState.buses.collectAsState()

    return remember(attendanceRes, marksRes, tasks, profile, busesRes) {
        val result = mutableListOf<CommandItem>()

        // ── 1. Static Navigation Commands ──
        val navCommands = listOf(
            CommandItem("nav-home", "Home", "Go to Dashboard", Icons.Rounded.Home, "Navigation", onSelect = { AppState.navigateTo(Screen.HOME) }),
            CommandItem("nav-attendance", "Attendance", "View your attendance", Icons.AutoMirrored.Rounded.FactCheck, "Navigation", onSelect = { AppState.navigateTo(Screen.ATTENDANCE) }),
            CommandItem("nav-academics", "Academics Hub", "All academic resources", Icons.Rounded.School, "Navigation", onSelect = { AppState.navigateTo(Screen.ACADEMICS) }),
            CommandItem("nav-payments", "Payments", "Fee payments & receipts", Icons.Rounded.CreditCard, "Navigation", onSelect = { AppState.navigateTo(Screen.PAYMENTS) }),
            CommandItem("nav-library", "Library", "Koha catalog and dues", Icons.AutoMirrored.Rounded.MenuBook, "Navigation", onSelect = { AppState.navigateTo(Screen.LIBRARIES) }),
            CommandItem("nav-hostel", "Hostel", "Hostel services & leave", Icons.Rounded.Apartment, "Navigation", onSelect = { AppState.navigateTo(Screen.HOSTEL) }),
            CommandItem("nav-transport", "Transport", "Bus routes & timings", Icons.Rounded.DirectionsBus, "Navigation", onSelect = { AppState.navigateTo(Screen.TRANSPORT) }),
            CommandItem("nav-events", "Events", "Upcoming fests & events", Icons.Rounded.Event, "Navigation", onSelect = { AppState.navigateTo(Screen.EVENTS) }),
            CommandItem("nav-qbank", "QBank", "Question papers", Icons.Rounded.Topic, "Navigation", onSelect = { AppState.navigateTo(Screen.QBANK) }),
            CommandItem("nav-social", "Social", "Student community", Icons.Rounded.People, "Navigation", onSelect = { AppState.navigateTo(Screen.SOCIAL) }),
            CommandItem("nav-profile", "Profile", "Your student details", Icons.Rounded.Person, "Navigation", onSelect = { AppState.navigateTo(Screen.PROFILE) }),
            CommandItem("nav-grades", "Grades", "Academic performance", Icons.Rounded.History, "Navigation", onSelect = { AppState.navigateTo(Screen.GRADES) }),
            CommandItem("nav-cgpa", "CGPA Predictor", "Predict your CGPA", Icons.AutoMirrored.Rounded.TrendingUp, "Navigation", onSelect = { AppState.navigateTo(Screen.GPA_PREDICTOR) }),
        )
        result.addAll(navCommands)

        // ── 2. Profile Details ──
        profile?.let { p ->
            result.add(
                CommandItem(
                    id = "profile-detail",
                    label = "Profile: ${p.name.ifEmpty { "Student" }}",
                    description = p.regNo,
                    icon = Icons.Rounded.Person,
                    category = "Profile",
                    onSelect = { AppState.navigateTo(Screen.PROFILE) }
                )
            )
        }

        // ── 3. Settings sub-screens ──
        SettingsSubScreen.entries.forEach { sub ->
            result.add(
                CommandItem(
                    id = "settings-${sub.name}",
                    label = sub.title,
                    description = sub.description,
                    icon = sub.icon,
                    category = "Settings",
                    onSelect = { AppState.openSettingsSection(sub.name) }
                )
            )
        }

        // ── 4. App Library modules (same single source as the Library sheet) ──
        appLibraryItems.forEach { item ->
            result.add(
                CommandItem(
                    id = "module-${item.label}",
                    label = item.label,
                    description = item.subLabel,
                    icon = item.icon,
                    category = "Modules",
                    onSelect = { executeAppLibraryItem(item) }
                )
            )
        }

        // ── 5. Attendance + Marks courses (deduped by code) ──
        val seenCourseCodes = mutableSetOf<String>()
        attendanceRes?.attendance?.forEach { course ->
            if (seenCourseCodes.add(course.courseCode)) {
                result.add(
                    CommandItem(
                        id = "att-${course.courseCode}",
                        label = course.courseTitle,
                        description = "Course · ${course.attendancePercentage}% attendance",
                        icon = Icons.Rounded.Class,
                        category = "Courses",
                        onSelect = { AppState.openCourseDetail(course.courseCode) }
                    )
                )
            }
        }
        marksRes?.marks?.forEach { m ->
            if (seenCourseCodes.add(m.courseCode)) {
                result.add(
                    CommandItem(
                        id = "marks-${m.courseCode}",
                        label = m.courseTitle.ifEmpty { m.courseCode },
                        description = "Course · ${m.courseCode}",
                        icon = Icons.Rounded.School,
                        category = "Courses",
                        onSelect = { AppState.openCourseDetail(m.courseCode) }
                    )
                )
            }
        }

        // ── 6. Tasks ──
        tasks.forEach { task ->
            result.add(
                CommandItem(
                    id = "task-${task.id}",
                    label = task.title,
                    description = "Due: ${task.dueDate}",
                    icon = Icons.Rounded.TaskAlt,
                    category = "Tasks",
                    onSelect = { AppState.navigateTo(Screen.TASKS) }
                )
            )
        }

        // ── 7. Transport routes (indexed directly — small dataset)
        busesRes?.buses?.forEach { route ->
            result.add(
                CommandItem(
                    id = "bus-${route.id}",
                    label = "Bus: ${route.route}",
                    description = "${route.stops.size} stops · ${route.type.ifBlank { "Route" }}",
                    icon = Icons.Rounded.DirectionsBus,
                    category = "Transport",
                    onSelect = { AppState.openTransportRoute(route.id) }
                )
            )
        }

        // ── 8. Sub-search entries (big / network-backed data, searched in-palette) ──

        // Library catalog (network-backed)
        result.add(
            CommandItem(
                id = "sub-library",
                label = "Library Catalog",
                description = "Search Koha books by title, author or ISBN",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                category = "Search Tools",
                subSearch = SubSearchSpec("library", "Library Catalog", "Title, author or ISBN...", Icons.AutoMirrored.Rounded.MenuBook) { query, _ ->
                    if (query.isBlank()) return@SubSearchSpec emptyList()
                    withContext(Dispatchers.Default) {
                        try {
                            val res = AmazeClient.searchLibrary(query)
                            res.searchResults.take(50).map { book ->
                                CommandItem(
                                    id = "lib-${book.bookId}",
                                    label = book.title,
                                    description = listOfNotNull(book.author, book.dueDate?.let { "Due $it" }).joinToString(" · "),
                                    icon = Icons.Rounded.MenuBook,
                                    onSelect = { AppState.navigateTo(Screen.LIBRARIES) }
                                )
                            }
                        } catch (_: Exception) { emptyList() }
                    }
                }
            )
        )

        // Faculty directory — two step: schools first, then faculty of the chosen school
        result.add(
            CommandItem(
                id = "sub-faculty",
                label = "Faculty Directory",
                description = "Pick a school, then find faculty by name, ID or email",
                icon = Icons.Rounded.People,
                category = "Search Tools",
                subSearch = SubSearchSpec("faculty", "Faculty Directory", "Search school or faculty…", Icons.Rounded.People) { query, ctx ->
                    val schoolId = ctx.store["schoolId"] as? String
                    if (schoolId == null) {
                        // Step 1: school selection cards
                        withContext(Dispatchers.Default) {
                            try {
                                val res = AmazeClient.getFacultySchools()
                                val q = query.trim().lowercase()
                                res.schools.filter { s ->
                                    q.isBlank() || s.school_name.lowercase().contains(q)
                                }.map { s ->
                                    CommandItem(
                                        id = "school-${s.id}",
                                        label = s.school_name,
                                        description = "Tap to view faculty",
                                        icon = Icons.Rounded.School,
                                        keepPaletteOpen = true,
                                        onSelect = { ctx.store["schoolId"] = s.id }
                                    )
                                }
                            } catch (_: Exception) { emptyList() }
                        }
                    } else {
                        // Step 2: faculty of the selected school
                        withContext(Dispatchers.Default) {
                            val roster = try { AmazeClient.postFacultyScrape(schoolId) } catch (_: Exception) { null }
                            if (roster?.success != true) return@withContext listOf(
                                CommandItem("faculty-error", "Could not load faculty", "Pull to retry via the screen", Icons.Rounded.SearchOff, onSelect = { AppState.navigateTo(Screen.FACULTY_INFO) })
                            )
                            val q = query.trim().lowercase()
                            val matches = roster.faculties.filter { f ->
                                q.isBlank() ||
                                        f.name.lowercase().contains(q) ||
                                        f.employeeId.lowercase().contains(q) ||
                                        f.designation.lowercase().contains(q) ||
                                        f.email.lowercase().contains(q)
                            }.sortedBy { it.name }
                            listOf(
                                CommandItem("school-back", "← Choose a different school", null, Icons.Rounded.ArrowBack, keepPaletteOpen = true, onSelect = { ctx.store.remove("schoolId") })
                            ) + matches.take(50).map { f ->
                                CommandItem(
                                    id = "fac-${f.employeeId}",
                                    label = f.name,
                                    description = listOfNotNull(f.employeeId.ifBlank { null }, f.designation, f.email.ifBlank { null }).joinToString(" · "),
                                    icon = Icons.Rounded.Person,
                                    onSelect = { AppState.openFaculty(schoolId, f.employeeId) }
                                )
                            }
                        }
                    }
                }
            )
        )

        // FFCS course catalog (large static dataset — filtered in-palette)
        result.add(
            CommandItem(
                id = "sub-ffcs",
                label = "FFCS Courses",
                description = "Browse every offered course by code or title",
                icon = Icons.Rounded.ViewTimeline,
                category = "Search Tools",
                subSearch = SubSearchSpec("ffcs", "FFCS Courses", "Search by code or title (e.g. BAME203, Thermodynamics)…", Icons.Rounded.ViewTimeline) { query, ctx ->
                    if (query.isBlank()) return@SubSearchSpec emptyList()
                    withContext(Dispatchers.Default) {
                        val courses = ctx.store.getOrPut("ffcsAll") {
                            FfcsCourseProcessor.processCourses(
                                FfcsCourseProcessor.parseFFCSCSV(FfcsReportData.CSV_DATA)
                            )
                        } as List<com.amazecc.app.shared.ffcs.ParsedCourse>
                        val q = query.trim().lowercase()
                        courses.asSequence()
                            .filter { c -> c.code.lowercase().contains(q) || c.title.lowercase().contains(q) }
                            .distinctBy { it.code }
                            .take(50)
                            .map { c ->
                                CommandItem(
                                    id = "ffcs-${c.code}",
                                    label = "${c.code} — ${c.title}",
                                    description = "${c.credits} credits · ${c.type.ifBlank { "Course" }}",
                                    icon = Icons.Rounded.MenuBook,
                                    onSelect = { AppState.openFfcsCourse(c.code) }
                                )
                            }
                            .toList()
                    }
                }
            )
        )

        // Curriculum courses (synced, large set)
        result.add(
            CommandItem(
                id = "sub-curriculum",
                label = "Curriculum Courses",
                description = "Search your degree's requirements by code or name",
                icon = Icons.Rounded.ImportContacts,
                category = "Search Tools",
                subSearch = SubSearchSpec("curriculum", "Curriculum", "Search by code or name...", Icons.Rounded.ImportContacts) { query, ctx ->
                    if (query.isBlank()) return@SubSearchSpec emptyList()
                    val curriculum = AppState.curriculum.value
                    val q = query.trim().lowercase()
                    val matches = mutableListOf<CommandItem>()
                    curriculum?.details?.forEach { cat ->
                        cat.baskets.forEach { basket ->
                            basket.items.filter { it.code.lowercase().contains(q) || it.name.lowercase().contains(q) }
                                .forEach { item ->
                                    matches.add(
                                        CommandItem(
                                            id = "cur-${cat.code}-${item.code}",
                                            label = item.name.ifEmpty { item.code },
                                            description = "${item.code} · ${item.credits} cr · ${cat.name}",
                                            icon = Icons.Rounded.ListAlt,
                                            onSelect = { AppState.openCurriculumCourse(item.code) }
                                        )
                                    )
                                }
                        }
                    }
                    matches.take(50)
                }
            )
        )

        // Free rooms (derived from the FFCS report: all campus rooms)
        result.add(
            CommandItem(
                id = "sub-rooms",
                label = "Free Rooms",
                description = "Search campus rooms by block (e.g. AB1, SJT)",
                icon = Icons.Rounded.MeetingRoom,
                category = "Search Tools",
                subSearch = SubSearchSpec("rooms", "Free Rooms", "Search room (e.g. 101, AB1)…", Icons.Rounded.MeetingRoom) { query, ctx ->
                    if (query.isBlank()) return@SubSearchSpec emptyList()
                    withContext(Dispatchers.Default) {
                        val rooms: List<String> = ctx.store.getOrPut("rooms") {
                            FfcsCourseProcessor.processCourses(
                                FfcsCourseProcessor.parseFFCSCSV(FfcsReportData.CSV_DATA)
                            ).mapNotNull { it.room.takeIf { r -> r.isNotBlank() && !r.startsWith("NIL", true) } }
                                .distinct()
                                .sorted()
                        } as List<String>
                        val q = query.trim().lowercase()
                        rooms.filter { it.lowercase().contains(q) }
                            .take(50)
                            .map { r ->
                                CommandItem(
                                    id = "room-$r",
                                    label = r,
                                    description = "Check availability at any time",
                                    icon = Icons.Rounded.MeetingRoom,
                                    onSelect = { AppState.openFreeRoom(r) }
                                )
                            }
                    }
                }
            )
        )

        result
    }
}