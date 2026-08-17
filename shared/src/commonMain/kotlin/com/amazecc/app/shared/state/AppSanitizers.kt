package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.model.CabShareUser
import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.CalendarsListRes
import com.amazecc.app.shared.model.CircularItem
import com.amazecc.app.shared.model.CircularsRes
import com.amazecc.app.shared.model.ClubsRes
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.EventHubEvent
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import com.amazecc.app.shared.model.EventHubRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.FfcsRegistrationInfo
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.HostelDetails
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.model.LaundryRes
import com.amazecc.app.shared.model.LibraryRes
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.model.MessMenuRes
import com.amazecc.app.shared.model.MoodleRes
import com.amazecc.app.shared.model.PaymentsRes
import com.amazecc.app.shared.model.QcmViewRes
import com.amazecc.app.shared.model.TransportDataRes
import com.amazecc.app.shared.utils.parseViewLink
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Deciphers raw endpoint payloads into clean, typed data for [AppDataStore].
 *
 * Every function returns a sanitised copy of the transport DTO containing ONLY
 * usable data: placeholders and blank identity keys are dropped here, never in
 * the UI. Raw JsonElement payloads (attendance viewLink, QCM data) are decoded
 * into typed fields and nulled out so no raw JSON survives in the store.
 */
object AppSanitizers {

    // ── Shared helpers ──

    private val PLACEHOLDERS = setOf(
        "-", "--", "—", "–", "not set", "tbd", "tba", "nil", "nill", "null", "n/a"
    )

    /** Trims and drops blanks/placeholder strings. */
    fun String?.clean(): String? =
        this?.trim()?.takeIf { it.isNotBlank() && it.lowercase() !in PLACEHOLDERS }

    /**
     * A real VIT course code has at least one letter and one digit in its base
     * (optionally a trailing `(L)`/`(T)` type tag). Rejects parser garbage like
     * `(T)` that VTOP emits when a row's code cell is empty.
     */
    fun isValidCourseCode(raw: String): Boolean {
        val base = raw.trim().removeSuffix("(L)").removeSuffix("(T)").trim()
        return base.any { it.isLetter() } && base.any { it.isDigit() }
    }

    /** Normalises an attendance-percentage string ("87%" / "87") to a plain number string. */
    fun cleanPercent(raw: String?): String? =
        raw?.trim()?.removeSuffix("%")?.trim()?.takeIf { it.isNotBlank() && it != "-" }

    /** Deciphers the raw viewLink payload into typed daily records. */
    fun decodeAttendanceLogs(viewLink: JsonElement?): List<AttendanceLog> {
        val parsed = parseViewLink(viewLink) ?: return emptyList()
        return when (parsed) {
            is JsonArray -> parsed.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val date = (obj["date"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val status = (obj["status"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                AttendanceLog(date, status)
            }
            is JsonObject -> parsed.entries.mapNotNull { (date, status) ->
                val d = date.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val s = (status as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                AttendanceLog(d, s)
            }
            else -> emptyList()
        }
    }

    // ── Attendance ──

    fun sanitizeAttendance(res: AttendanceRes?): AttendanceRes? {
        if (res == null) return null
        val items = res.attendance.orEmpty().mapNotNull { item ->
            val code = item.courseCode.clean() ?: return@mapNotNull null // no courseCode -> discard
            if (!isValidCourseCode(code)) return@mapNotNull null // "(T)" garbage rows -> discard
            item.copy(
                courseCode = code,
                courseTitle = item.courseTitle.clean() ?: "",
                courseType = item.courseType.clean() ?: "",
                slotName = item.slotName.clean() ?: "",
                faculty = item.faculty.clean() ?: "",
                attendancePercentage = item.attendancePercentage.trim().takeIf { it.isNotBlank() } ?: "",
                credits = item.credits.clean(),
                slotVenue = item.slotVenue.clean(),
                category = item.category.clean(),
                logs = decodeAttendanceLogs(item.viewLinkRaw),
                viewLinkRaw = null
            )
        }
        return res.copy(attendance = items)
    }

    // ── Marks ──

    fun sanitizeMarks(res: MarksRes?): MarksRes? {
        if (res == null) return null
        val source = if (res.marksKey.isNotEmpty()) res.marksKey else res.courses
        val courses = source.mapNotNull { course ->
            val code = course.courseCode.clean() ?: return@mapNotNull null
            if (!isValidCourseCode(code)) return@mapNotNull null
            course.copy(
                courseCode = code,
                courseTitle = course.courseTitle.clean() ?: "",
                courseType = course.courseType.clean() ?: "",
                courseSystem = course.courseSystem.clean() ?: "",
                faculty = course.faculty.clean() ?: "",
                slot = course.slot.clean() ?: "",
                classNbr = course.classNbr.clean() ?: "",
                assessments = course.assessments.mapNotNull { a ->
                    val title = a.title.clean() ?: return@mapNotNull null
                    a.copy(
                        title = title,
                        maxMark = a.maxMark.clean() ?: "",
                        weightagePercent = a.weightagePercent.clean() ?: "",
                        status = a.status.clean() ?: "",
                        scoredMark = a.scoredMark.clean() ?: "",
                        weightageMark = a.weightageMark.clean() ?: ""
                    )
                }
            )
        }
        return res.copy(
            courses = courses,
            marksKey = emptyList(),
            cgpa = res.cgpa?.let {
                it.copy(creditsEarned = it.creditsEarned.clean(), cgpa = it.cgpa.clean())
            }
        )
    }

    // ── Grade history ──

    fun sanitizeAllGrades(res: AllGradesRes?): AllGradesRes? {
        if (res == null) return null
        val cleaned = res.grades?.mapValues { (_, sem) ->
            if (sem == null) null
            else sem.copy(
                gpa = sem.gpa.clean(),
                grades = sem.grades.mapNotNull { g ->
                    val code = g.courseCode.clean() ?: return@mapNotNull null
                    if (!isValidCourseCode(code)) return@mapNotNull null
                    g.copy(
                        courseCode = code,
                        courseTitle = g.courseTitle.clean() ?: "",
                        courseType = g.courseType.clean() ?: "",
                        grandTotal = g.grandTotal.clean() ?: "",
                        grade = g.grade.clean() ?: "",
                        details = g.details?.mapNotNull { d ->
                            val component = d.component.clean() ?: return@mapNotNull null
                            d.copy(
                                component = component,
                                maxMark = d.maxMark.clean() ?: "",
                                weightagePercent = d.weightagePercent.clean() ?: "",
                                status = d.status.clean() ?: "",
                                scoredMark = d.scoredMark.clean() ?: "",
                                weightageMark = d.weightageMark.clean() ?: ""
                            )
                        },
                        range = g.range?.let { r ->
                            r.copy(
                                S = r.S.clean() ?: "", A = r.A.clean() ?: "",
                                B = r.B.clean() ?: "", C = r.C.clean() ?: "",
                                D = r.D.clean() ?: "", E = r.E.clean() ?: "", F = r.F.clean() ?: ""
                            )
                        }
                    )
                }
            )
        }
        return res.copy(grades = cleaned)
    }

    // ── Exam schedule ──

    fun sanitizeExamSchedule(res: ExamScheduleRes?): ExamScheduleRes? {
        if (res == null) return null
        val cleaned = res.schedule.mapValues { (_, items) ->
            items.mapNotNull { e ->
                val code = e.courseCode.clean() ?: return@mapNotNull null
                if (!isValidCourseCode(code)) return@mapNotNull null
                e.copy(
                    courseCode = code,
                    courseTitle = e.courseTitle.clean() ?: "",
                    classId = e.classId.clean() ?: "",
                    slot = e.slot.clean() ?: "",
                    examDate = e.examDate.clean() ?: "",
                    examSession = e.examSession.clean() ?: "",
                    reportingTime = e.reportingTime.clean() ?: "",
                    examTime = e.examTime.clean() ?: "",
                    venue = e.venue.clean() ?: "",
                    seatLocation = e.seatLocation.clean() ?: "",
                    seatNo = e.seatNo.clean() ?: ""
                )
            }
        }
        return res.copy(rawScheduleUpper = cleaned, rawScheduleLower = null)
    }

    // ── Calendar ──

    fun sanitizeCalendar(res: CalendarRes?): CalendarRes? {
        if (res == null) return null
        return res.copy(months = res.months.mapNotNull { m ->
            val month = m.month.clean() ?: return@mapNotNull null
            m.copy(month = month, days = m.days.mapNotNull { d ->
                val events = d.events.mapNotNull { e ->
                    val text = e.text.clean() ?: return@mapNotNull null
                    e.copy(text = text, type = e.type.clean() ?: "Other", category = e.category.clean() ?: "", color = e.color.clean())
                }
                if (events.isEmpty()) null else CalendarDay(d.date, events)
            })
        })
    }

    fun sanitizeCalendarsList(res: CalendarsListRes?): CalendarsListRes? {
        if (res == null) return null
        return res.copy(calendars = res.calendars.mapNotNull { c ->
            val name = c.name.clean() ?: return@mapNotNull null
            val months = c.months.mapNotNull { m ->
                val month = m.month.clean() ?: return@mapNotNull null
                m.copy(month = month, days = m.days.mapNotNull { d ->
                    val events = d.events.mapNotNull { e ->
                        val text = e.text.clean() ?: return@mapNotNull null
                        e.copy(text = text, type = e.type.clean() ?: "Other", category = e.category.clean() ?: "", color = e.color.clean())
                    }
                    if (events.isEmpty()) null else CalendarDay(d.date, events)
                })
            }
            c.copy(name = name, months = months)
        })
    }

    // ── QCM (raw JsonElement -> typed tables) ──

    fun sanitizeQcmView(res: QcmViewRes?): QcmViewRes? {
        if (res == null) return null
        val tables = decodeQcmTables(res.data)
        return res.copy(tables = tables, data = null)
    }

    fun decodeQcmTables(data: JsonElement?): List<StoredQcmTable> {
        if (data == null) return emptyList()
        val tables = when (data) {
            is JsonArray -> data.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                objToTable(obj)
            }
            is JsonObject -> data.values.mapNotNull { value ->
                val obj = value as? JsonObject ?: return@mapNotNull null
                // Server shape: { <semesterId>: { semester, tables: [{caption, headers, rows}], keyValuePairs } }
                val rawTables = obj["tables"] as? JsonArray
                if (rawTables != null && rawTables.isNotEmpty()) {
                    val semester = (obj["semester"] as? JsonPrimitive)?.contentOrNull?.clean() ?: ""
                    rawTables.mapNotNull { t ->
                        val tableObj = t as? JsonObject ?: return@mapNotNull null
                        tableObjToTable(tableObj, semester)
                    }
                } else {
                    // Legacy/other shapes: { caption, rows } per value
                    listOfNotNull(objToTable(obj).takeIf { it.rows.isNotEmpty() || it.caption != null })
                }
            }.flatten()
            else -> emptyList()
        }
        return tables.filter { it.rows.isNotEmpty() || it.caption != null }
    }

    private fun tableObjToTable(tableObj: JsonObject, semester: String): StoredQcmTable? {
        val caption = (tableObj["caption"] as? JsonPrimitive)?.contentOrNull?.clean()
            ?: semester.ifBlank { null }
        val rows = (tableObj["rows"] as? JsonArray).orEmpty().mapNotNull { row ->
            val rowObj = row as? JsonObject ?: return@mapNotNull null
            StoredQcmRow(
                qcmNo = rowObj.stringOf("qcmNo", "QCM No", "qcm no"),
                action = rowObj.stringOf("actionTaken", "Action Taken", "action taken"),
                suggestions = rowObj.stringOf("suggestions", "Suggestions"),
                facultyReply = rowObj.stringOf("facultyReply", "Faculty Reply", "faculty reply")
            )
        }.filter { row ->
            row.qcmNo != null || row.action != null || row.suggestions != null || row.facultyReply != null
        }
        if (rows.isEmpty()) return null
        return StoredQcmTable(caption = caption, rows = rows)
    }

    private fun objToTable(obj: JsonObject): StoredQcmTable {
        val caption = (obj["caption"] as? JsonPrimitive)?.contentOrNull?.clean()
        val rows = (obj["rows"] as? JsonArray).orEmpty().mapNotNull { row ->
            val rowObj = row as? JsonObject ?: return@mapNotNull null
            StoredQcmRow(
                qcmNo = rowObj.stringOf("qcmNo", "QCM No", "qcm no"),
                action = rowObj.stringOf("actionTaken", "Action Taken", "action taken"),
                suggestions = rowObj.stringOf("suggestions", "Suggestions"),
                facultyReply = rowObj.stringOf("facultyReply", "Faculty Reply", "faculty reply")
            )
        }.filter { row ->
            row.qcmNo != null || row.action != null || row.suggestions != null || row.facultyReply != null
        }
        return StoredQcmTable(caption = caption, rows = rows)
    }

    private fun JsonObject.stringOf(vararg keys: String): String? {
        for (key in keys) {
            val v = (this[key] as? JsonPrimitive)?.contentOrNull?.clean()
            if (v != null) return v
        }
        return null
    }

    // ── Curriculum ──

    fun sanitizeCurriculum(res: CurriculumRes?): CurriculumRes? {
        if (res == null) return null
        return res.copy(
            title = res.title.clean() ?: "",
            categories = res.categories.mapNotNull { c ->
                val code = c.code.clean() ?: return@mapNotNull null
                c.copy(code = code, name = c.name.clean() ?: "")
            },
            details = res.details.mapNotNull { d ->
                val code = d.code.clean() ?: return@mapNotNull null
                d.copy(code = code, name = d.name.clean() ?: "",
                    baskets = d.baskets.mapNotNull { b ->
                        val title = b.title.clean() ?: return@mapNotNull null
                        b.copy(title = title, items = b.items.mapNotNull { i ->
                            val iCode = i.code.clean() ?: return@mapNotNull null
                            i.copy(code = iCode, name = i.name.clean() ?: "", type = i.type.clean())
                        })
                    }
                )
            }
        )
    }

    // ── Hostel ──

    fun sanitizeHostelDetails(res: HostelDetails?): HostelDetails? {
        if (res == null) return null
        val info = res.hostelInfo?.let { h ->
            h.copy(
                gender = h.gender.clean(),
                blockName = h.blockName.clean(),
                roomNo = h.roomNo.clean(),
                messInfo = h.messInfo.clean()
            )
        }
        val leaves = res.leaveHistory.mapNotNull { l ->
            val cleaned = l.copy(
                visitPlace = l.visitPlace.clean(),
                reason = l.reason.clean(),
                leaveType = l.leaveType.clean(),
                from = l.from.clean(),
                to = l.to.clean(),
                status = l.status.clean()
            )
            if (cleaned.from == null && cleaned.to == null && cleaned.reason == null && cleaned.status == null) null
            else cleaned
        }
        return res.copy(hostelInfo = info, leaveHistory = leaves)
    }

    fun sanitizeMessMenu(res: MessMenuRes?): MessMenuRes? {
        if (res == null) return null
        return res.copy(list = res.list.mapNotNull { d ->
            val cleaned = d.copy(
                Day = d.Day?.trim()?.takeIf { it.isNotBlank() },
                Breakfast = d.Breakfast?.trim()?.takeIf { it.isNotBlank() },
                Lunch = d.Lunch?.trim()?.takeIf { it.isNotBlank() },
                Snacks = d.Snacks?.trim()?.takeIf { it.isNotBlank() },
                Dinner = d.Dinner?.trim()?.takeIf { it.isNotBlank() }
            )
            if (cleaned.Day == null && cleaned.Breakfast == null && cleaned.Lunch == null &&
                cleaned.Snacks == null && cleaned.Dinner == null
            ) null else cleaned
        })
    }

    fun sanitizeLaundry(res: LaundryRes?): LaundryRes? {
        if (res == null) return null
        return res.copy(list = res.list.mapNotNull { s ->
            val date = s.Date?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            s.copy(Date = date, RoomNumber = s.RoomNumber?.trim()?.takeIf { it.isNotBlank() })
        })
    }

    fun sanitizeCounselling(res: ArrearResponse?): ArrearResponse? {
        if (res == null) return null
        return res.copy(
            tables = res.tables.mapNotNull { t ->
                val rows = t.rows.mapNotNull { row ->
                    val cells = row.map { it.trim() }
                    if (cells.all { it.isBlank() }) null else cells
                }
                if (rows.isEmpty()) null else t.copy(rows = rows)
            },
            keyValuePairs = res.keyValuePairs.mapNotNull { p ->
                val label = p.label.clean() ?: return@mapNotNull null
                val value = p.value.clean() ?: return@mapNotNull null
                com.amazecc.app.shared.model.KeyValuePair(label, value)
            }
        )
    }

    // ── Payments / Library / Transport / Buses / LMS ──

    fun sanitizePayments(res: PaymentsRes?): PaymentsRes? {
        if (res == null) return null
        return res.copy(
            payments = res.payments.mapNotNull { p ->
                val id = p.billingId.clean() ?: return@mapNotNull null
                p.copy(
                    billingId = id,
                    description = p.description.clean() ?: "",
                    amount = p.amount.clean() ?: "0",
                    dueDate = p.dueDate.clean(),
                    status = p.status.clean() ?: "",
                    paymentDate = p.paymentDate.clean(),
                    receiptNo = p.receiptNo.clean()
                )
            },
            walletBalance = res.walletBalance.clean()
        )
    }

    fun sanitizeLibrary(res: LibraryRes?): LibraryRes? {
        if (res == null) return null
        return res.copy(
            booksIssued = res.booksIssued.mapNotNull { b ->
                val id = b.bookId.clean() ?: return@mapNotNull null
                val title = b.title.clean() ?: return@mapNotNull null
                b.copy(bookId = id, title = title, author = b.author.clean(), dueDate = b.dueDate.clean(), fineAmount = b.fineAmount.clean())
            },
            searchResults = res.searchResults.mapNotNull { b ->
                val id = b.bookId.clean() ?: return@mapNotNull null
                val title = b.title.clean() ?: return@mapNotNull null
                b.copy(bookId = id, title = title, author = b.author.clean(), dueDate = b.dueDate.clean(), fineAmount = b.fineAmount.clean())
            }
        )
    }

    fun sanitizeTransportData(res: TransportDataRes?): TransportDataRes? {
        if (res == null) return null
        return res.copy(
            registerNumber = res.registerNumber.clean(),
            name = res.name.clean(),
            branch = res.branch.clean(),
            routeSelected = res.routeSelected.clean(),
            busRouteId = res.busRouteId.clean(),
            qrCode = res.qrCode.clean()
        )
    }

    fun sanitizeBuses(res: BusesRes?): BusesRes? {
        if (res == null) return null
        return res.copy(buses = res.buses.mapNotNull { b ->
            val id = b.id.clean() ?: return@mapNotNull null
            b.copy(
                id = id,
                type = b.type.clean() ?: "",
                route = b.route.clean() ?: "",
                boardingPoints = b.boardingPoints.mapNotNull { it.trim().takeIf(String::isNotBlank) },
                driverPhone = b.driverPhone.clean() ?: "",
                driverName = b.driverName.clean() ?: "",
                whatsappGroup = b.whatsappGroup.clean() ?: "",
                busLocation = b.busLocation.clean() ?: "",
                supervisorName = b.supervisorName.clean(),
                supervisorPhone = b.supervisorPhone.clean(),
                driverInchargeName = b.driverInchargeName.clean(),
                driverInchargePhone = b.driverInchargePhone.clean(),
                stops = b.stops.mapNotNull { s ->
                    val name = s.stopName.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    s.copy(stopName = name, pickupTime = s.pickupTime?.trim()?.takeIf { it.isNotBlank() })
                }
            )
        })
    }

    fun sanitizeLms(res: LMSRes?): LMSRes? {
        if (res == null) return null
        return res.copy(assignments = res.assignments.mapNotNull { a ->
            val id = a.assignmentId.clean() ?: return@mapNotNull null
            a.copy(
                assignmentId = id,
                courseCode = a.courseCode.clean() ?: "",
                title = a.title.clean() ?: "",
                maxMarks = a.maxMarks.clean() ?: "",
                dueDate = a.dueDate.clean() ?: "",
                status = a.status.clean() ?: "Pending",
                score = a.score.clean()
            )
        })
    }

    // ── Events / Registered events / Clubs ──

    fun sanitizeEvents(res: EventHubRes?): EventHubRes? {
        if (res == null) return null
        return res.copy(events = res.events.mapNotNull(::sanitizeEventHubEvent))
    }

    fun sanitizeEventHubEvent(e: EventHubEvent): EventHubEvent? {
        val id = e.eid.clean() ?: return null
        return e.copy(
            eid = id,
            title = e.title.clean() ?: "",
            eligibility = e.eligibility.clean() ?: "",
            type = e.type.clean() ?: "",
            date = e.date.clean() ?: "",
            location = e.location.clean() ?: "",
            price = e.price.clean() ?: "",
            time = e.time.clean(),
            posterUrl = e.posterUrl.clean()
        )
    }

    fun sanitizeRegisteredEvents(res: EventHubRegisteredEventsRes?): EventHubRegisteredEventsRes? {
        if (res == null) return null
        return res.copy(events = res.events.mapNotNull { e ->
            val id = e.eid.clean() ?: return@mapNotNull null
            e.copy(eid = id, title = e.title.clean() ?: "", location = e.location.clean() ?: "", date = e.date.clean(), time = e.time.clean())
        })
    }

    fun sanitizeClubs(res: ClubsRes?): ClubsRes? {
        if (res == null) return null
        return res.copy(clubs = res.clubs.mapNotNull { c ->
            val id = c.id.clean()
            val name = c.name.clean() ?: return@mapNotNull null
            if (id == null && name == null) null
            else c.copy(id = id, name = name, description = c.description.clean(), logoUrl = c.logoUrl.clean(), website = c.website.clean(), instagram = c.instagram.clean(), whatsapp = c.whatsapp.clean())
        })
    }

    // ── Circulars (recursive) ──

    fun sanitizeCirculars(res: CircularsRes?): CircularsRes? {
        if (res == null) return null
        return res.copy(circulars = res.circulars.mapNotNull(::sanitizeCircularItem))
    }

    private fun sanitizeCircularItem(item: CircularItem): CircularItem? {
        val title = item.title?.clean() ?: return null
        val children = item.children.orEmpty().mapNotNull(::sanitizeCircularItem)
        return CircularItem(id = item.id?.clean(), title = title, children = children.takeIf { it.isNotEmpty() })
    }

    // ── Moodle / CabShare ──

    fun sanitizeMoodle(res: MoodleRes?): MoodleRes? {
        if (res == null) return null
        return res.copy(data = res.data.mapNotNull { a ->
            val name = a.name.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            a.copy(name = name, due = a.due.trim().takeIf { it.isNotBlank() } ?: "", teachers = a.teachers.mapNotNull { it.trim().takeIf(String::isNotBlank) })
        })
    }

    fun sanitizeCabShareUser(res: CabShareUser?): CabShareUser? {
        if (res == null) return null
        val reg = res.reg_number.trim().takeIf { it.isNotBlank() } ?: return null
        return res.copy(reg_number = reg, name = res.name.trim(), phone_number = res.phone_number.trim())
    }

    // ── FFCS registration slot ──

    fun sanitizeFfcs(info: FfcsRegistrationInfo?): FfcsRegistrationInfo? {
        if (info == null) return null
        val date = info.date.clean() ?: return null
        return FfcsRegistrationInfo(
            userName = info.userName.clean() ?: "",
            date = date,
            fromTime = info.fromTime.clean() ?: "",
            toTime = info.toTime.clean() ?: ""
        )
    }

    // ── Tasks (local user data — light sanitisation) ──

    fun sanitizeTasks(tasks: List<HomeworkTask>): List<HomeworkTask> =
        tasks.mapNotNull { t ->
            val id = t.id.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            t.copy(id = id, title = t.title.trim().takeIf { it.isNotBlank() } ?: t.title)
        }
}
