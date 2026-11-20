package com.amazecc.app.shared.model

import com.amazecc.app.shared.api.amazeAuthHeaders
import com.amazecc.app.shared.api.arrearFromBulkResult
import com.amazecc.app.shared.api.attendanceContract
import com.amazecc.app.shared.api.bulkContract
import com.amazecc.app.shared.api.cabshareGuardedContract
import com.amazecc.app.shared.api.cabshareHubsContract
import com.amazecc.app.shared.api.curvesParams
import com.amazecc.app.shared.api.ffcsContract
import com.amazecc.app.shared.api.lmsFromMoodle
import com.amazecc.app.shared.api.marksFromAttendance
import com.amazecc.app.shared.api.semesterGradesFromAll
import com.amazecc.app.shared.api.timetableFromAttendance
import com.amazecc.app.shared.repository.SessionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drift fixes D-016..D-027, D-090, D-091, D-098, D-099, D-107.
 * Payloads below are copied from the live Hono/TS shapes, not invented.
 */
class ContractClientParityTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ── D-011: Amaze ID token state ─────────────────────────────────────────
    @Test
    fun `amaze id token persists and auth required signal is explicit`() {
        val originalToken = SessionManager.amazeAccessToken.value
        val originalDpop = SessionManager.amazeDpopHeader.value
        try {
            SessionManager.clearAmazeIdSession()
            assertFalse(SessionManager.hasAmazeIdAuth)
            SessionManager.saveAmazeIdSession("access-123", "dpop-proof")
            assertTrue(SessionManager.hasAmazeIdAuth)
            assertEquals("access-123", SessionManager.amazeAccessToken.value)
            assertEquals("dpop-proof", SessionManager.amazeDpopHeader.value)
            SessionManager.markAmazeAuthRequired()
            assertTrue(SessionManager.amazeAuthRequired.value)
            SessionManager.clearAmazeAuthRequired()
            assertFalse(SessionManager.amazeAuthRequired.value)
        } finally {
            SessionManager.saveAmazeIdSession(originalToken.orEmpty(), originalDpop)
            SessionManager.clearAmazeAuthRequired()
        }
    }

    // ── D-016 / D-106: QBankPaper ───────────────────────────────────────────
    @Test
    fun `qbank paper decodes full papers_archive row with aliases`() {
        val payload = """
            {
              "source_id": "QB-1042",
              "title": "CAT 1 2025",
              "file_url": "https://cdn.example/paper.pdf",
              "source_type": "CAT 1",
              "exam_semester": "Fall",
              "exam_year": 2025,
              "course_code": "BACS3001",
              "approval_status": "APPROVED"
            }
        """.trimIndent()
        val paper = json.decodeFromString<QBankPaper>(payload)
        assertEquals("QB-1042", paper.sourceId)
        assertEquals("https://cdn.example/paper.pdf", paper.link)
        assertEquals("CAT 1", paper.type)
        assertEquals("2025", paper.examYear)
        assertEquals("BACS3001", paper.courseCode)
    }

    @Test
    fun `qbank paper decodes partial and legacy rows without throwing`() {
        val partial = json.decodeFromString<QBankPaper>("""{"title":"Only a title"}""")
        assertEquals("Only a title", partial.title)
        assertEquals("", partial.link)
        assertEquals("", partial.type)

        val legacy = json.decodeFromString<QBankPaper>(
            """{"title":"Legacy","link":"https://old","type":"Quiz"}"""
        )
        assertEquals("https://old", legacy.link)
        assertEquals("Quiz", legacy.type)
    }

    @Test
    fun `qbank papers envelope with mixed rows still decodes`() {
        val payload = """
            {"success":true,"data":[
              {"title":"Full","file_url":"https://a","source_type":"CAT 1"},
              {"title":"Partial"}
            ]}
        """.trimIndent()
        val res = json.decodeFromString<QBankPapersRes>(payload)
        assertEquals(2, res.data.size)
        assertEquals("https://a", res.data[0].link)
        assertEquals("", res.data[1].link)
    }

    // ── D-023: QBankQuestion ────────────────────────────────────────────────
    @Test
    fun `qbank question carries joined paper metadata and image`() {
        val payload = """
            {"question_id":"Q1","question_text":"Pick one","question_type":"MCQ",
             "source_type":"CAT 1","exam_year":2025,"image_url":"https://img/q1.png",
             "exam_semester":"Fall"}
        """.trimIndent()
        val q = json.decodeFromString<QBankQuestion>(payload)
        assertEquals("CAT 1", q.source_type)
        assertEquals("2025", q.exam_year)
        assertEquals("https://img/q1.png", q.image_url)
    }

    // ── D-027: QBankCoursesRes ──────────────────────────────────────────────
    @Test
    fun `qbank courses decodes data alias and keeps legacy courses`() {
        val api = json.decodeFromString<QBankCoursesRes>(
            """{"success":true,"data":[{"code":"BACS3001","title":"OS"}]}"""
        )
        assertEquals("BACS3001", api.effectiveCourses.single().courseCode)
        assertEquals("OS", api.effectiveCourses.single().courseTitle)

        val legacy = json.decodeFromString<QBankCoursesRes>(
            """{"success":true,"courses":[{"courseCode":"BACSE102","courseTitle":"Java"}]}"""
        )
        assertEquals("BACSE102", legacy.effectiveCourses.single().courseCode)
    }

    // ── D-017: LibraryRes nested library.books ──────────────────────────────
    @Test
    fun `library finance payload decodes nested books with numeric types`() {
        val payload = """
            {
              "success": true,
              "dues": {"hasDues": false},
              "library": {
                "books": [
                  {"title":"Discrete Mathematics","author":"Rosen","isbn":"978-0132439871",
                   "dueAmount": 90,"daysOverdue": 7}
                ],
                "tables": [{"headers":["Title"],"rows":[{"Title":"Discrete Mathematics"}]}],
                "keyValuePairs": {"Student":"21BCE1001"}
              }
            }
        """.trimIndent()
        val res = json.decodeFromString<LibraryRes>(payload)
        val book = res.libraryDues!!.books.single()
        assertEquals("978-0132439871", book.isbn)
        assertEquals("90", book.dueAmount)
        assertEquals("7", book.daysOverdue)
        assertEquals(1, res.effectiveBooks.size)
        assertEquals("90", res.effectiveBooks.single().fineAmount)
    }

    // ── D-022: typed finance sections ───────────────────────────────────────
    @Test
    fun `finance response keeps receipts refunds and USD ledger`() {
        val payload = """
            {
              "success": true,
              "dues": {"hasDues": true, "message": "Pending dues",
                       "studentInfo": {"name":"A","programme":"B.Tech","campus":"CHN"}},
              "receipts": {"receipts":[
                 {"receiptNumber":"RCP-1","date":"01-01-2026","amount":"1200","applNo":"A1"}
              ]},
              "wallet": {
                "ledgerINR":[
                  {"transactionDate":"02-01-2026","receiptNumber":"W-1","amount":"500",
                   "bookBalanceAmount":"1500","particulars":"Refund","refundAmount":"500"}
                ],
                "ledgerUSD":[{"transactionDate":"03-01-2026","amount":"10","bookBalanceAmount":"20"}]
              },
              "library": {"books":[]}
            }
        """.trimIndent()
        val finance = json.decodeFromString<FinanceResponse>(payload)
        assertEquals(true, finance.dues?.hasDues)
        assertEquals("A1", finance.receipts?.receipts?.single()?.applNo)
        assertEquals("500", finance.wallet?.ledgerINR?.single()?.refundAmount)
        assertEquals("10", finance.wallet?.ledgerUSD?.single()?.amount)
    }

    // ── D-018 / D-025: EventHub ─────────────────────────────────────────────
    @Test
    fun `partial event registration keeps identity and adds new fields`() {
        val payload = """
            {"name":"Hackathon","venue":"Hall 1","eid":"EV-9",
             "registrationDate":"2026-11-01","certificateEligible":true}
        """.trimIndent()
        val reg = json.decodeFromString<EventHubRegisteredEvent>(payload)
        assertEquals("Hackathon", reg.title)
        assertEquals("Hall 1", reg.location)
        assertEquals("2026-11-01", reg.registrationDate)
        assertEquals(true, reg.certificateEligible)
        assertNull(reg.receiptUrl)
    }

    @Test
    fun `event registration with only a name no longer throws`() {
        val reg = json.decodeFromString<EventHubRegisteredEvent>("""{"name":"Free Talk"}""")
        assertEquals("Free Talk", reg.title)
        assertEquals("", reg.eid)
        assertEquals("", reg.location)
    }

    @Test
    fun `event hub event decodes registeredDetails and isPastEvent`() {
        val payload = """
            {"eid":"EV-1","title":"Quiz","eligibility":"All","type":"TECH","date":"2026-12-01",
             "location":"Room 1","price":"0","registeredDetails":{"count":12},"isPastEvent":false}
        """.trimIndent()
        val event = json.decodeFromString<EventHubEvent>(payload)
        assertNotNull(event.registeredDetails)
        assertEquals(false, event.isPastEvent)
    }

    // ── D-019: CGPA ─────────────────────────────────────────────────────────
    @Test
    fun `cgpa carries credits required and non graded requirement`() {
        val cgpa = json.decodeFromString<CGPAResult>(
            """{"creditsEarned":"120","cgpa":"8.85","creditsRequired":160,
                "nonGradedRequirement":4}"""
        )
        assertEquals("120", cgpa.creditsEarned)
        assertEquals("160", cgpa.creditsRequired)
        assertEquals("4", cgpa.nonGradedRequirement)
    }

    // ── D-020: hostel leave ─────────────────────────────────────────────────
    @Test
    fun `leave item carries leaveId and remarks`() {
        val leave = json.decodeFromString<LeaveItem>(
            """{"leaveId":"L-7","remarks":"Approved","visitPlace":"Chennai"}"""
        )
        assertEquals("L-7", leave.leaveId)
        assertEquals("Approved", leave.remarks)
    }

    // ── D-021: transport ────────────────────────────────────────────────────
    @Test
    fun `transport data decodes programme fp reference payment status and page csrf`() {
        val res = json.decodeFromString<TransportDataRes>(
            """{"success":true,"hasRegistration":true,"programme":"B.Tech",
                "fpReference":"FP-42","paymentStatus":"PAID","pageCsrf":"csrf-1"}"""
        )
        assertEquals("B.Tech", res.programme)
        assertEquals("FP-42", res.fpReference)
        assertEquals("PAID", res.paymentStatus)
        assertEquals("csrf-1", res.pageCsrf)
    }

    // ── D-024: Student profile regNo aliases ────────────────────────────────
    @Test
    fun `student profile accepts registerNo alias`() {
        val p = json.decodeFromString<StudentProfile>(
            """{"registerNo":"21BCE1001","name":"Ada","applicationNumber":"APP-1"}"""
        )
        assertEquals("21BCE1001", p.regNo)
    }

    @Test
    fun `student profile accepts registerNumber and registrationNumber aliases`() {
        assertEquals("21BCE1002", json.decodeFromString<StudentProfile>(
            """{"registerNumber":"21BCE1002"}"""
        ).regNo)
        assertEquals("21BCE1003", json.decodeFromString<StudentProfile>(
            """{"registrationNumber":"21BCE1003"}"""
        ).regNo)
        assertEquals("APP-9", json.decodeFromString<StudentProfile>(
            """{"applicationNumber":"APP-9"}"""
        ).regNo)
    }

    // ── D-026: GradeItem.slNo + CircularsRes.title ──────────────────────────
    @Test
    fun `grade item and circulars carry ordinal and title`() {
        val grade = json.decodeFromString<GradeItem>(
            """{"slNo":"3","courseCode":"BACS3001","grade":"A"}"""
        )
        assertEquals("3", grade.slNo)
        val circulars = json.decodeFromString<CircularsRes>(
            """{"title":"Academic Circulars","circulars":[]}"""
        )
        assertEquals("Academic Circulars", circulars.title)
    }

    // ── D-098: QCM semesters objects ────────────────────────────────────────
    @Test
    fun `qcm view decodes semester option objects and legacy strings`() {
        val api = json.decodeFromString<QcmViewRes>(
            """{"success":true,"semesters":[{"value":"CH20262701","text":"Fall 2026-27"}]}"""
        )
        assertEquals(listOf("CH20262701"), api.semesters)

        val legacy = json.decodeFromString<QcmViewRes>(
            """{"success":true,"semesters":["CH20262701","CH20262702"]}"""
        )
        assertEquals(listOf("CH20262701", "CH20262702"), legacy.semesters)
    }

    // ── D-107: marksRes may be a string ─────────────────────────────────────
    @Serializable
    private data class MarksEnvelope(
        @Serializable(with = MarksResOrStringSerializer::class)
        val marksRes: MarksRes? = null
    )

    @Test
    fun `attendance marksRes error string does not fail decode`() {
        val objectPayload = json.decodeFromString<MarksEnvelope>(
            """{"marksRes":{"success":true,"courses":[]}}"""
        )
        assertNotNull(objectPayload.marksRes)

        val stringPayload = json.decodeFromString<MarksEnvelope>(
            """{"marksRes":"VTOP marks scrape failed"}"""
        )
        assertNull(stringPayload.marksRes)

        val nullPayload = json.decodeFromString<MarksEnvelope>("""{"marksRes":null}""")
        assertNull(nullPayload.marksRes)
    }

    @Serializable
    private data class AttEnvelope(val attRes: AttendanceRes? = null)

    @Test
    fun `attendance response decodes attRes with nested semester`() {
        val p = """
            {"attRes":{"semester":"CH20262701","attendance":[
              {"courseCode":"BACSE102","courseTitle":"Java","attendedClasses":32,
               "totalClasses":36,"attendancePercentage":"88.89","classId":"C1",
               "credits":"4","slotVenue":"SJT-301","category":"THEORY"}
            ]},"marksRes":"failed"}
        """.trimIndent()
        val res = json.decodeFromString<AttEnvelope>(p).attRes!!
        assertEquals("CH20262701", res.semester)
        assertEquals(1, res.attendance!!.size)
    }

    // ── D-090: derive timetable / marks from attendance ─────────────────────
    @Test
    fun `timetable derived from merged attendance columns`() {
        val att = AttendanceItem(
            courseCode = "BACSE102", courseTitle = "Java", classId = "C1",
            credits = "4", slotVenue = "SJT-301", category = "THEORY",
            faculty = "DR XYZ", slNo = "1"
        )
        val tt = timetableFromAttendance("CH20262701", listOf(att))
        assertEquals("CH20262701", tt.semesterId)
        val course = tt.courseInfo!!.single()
        assertEquals("Java", course.course)
        assertEquals("BACSE102", course.courseCode)
        assertEquals("C1", course.classId)
        assertEquals("4", course.LTPJC)
        assertEquals("SJT-301", course.slotVenue)
        assertEquals("DR XYZ", course.facultyDetails)
    }

    @Test
    fun `marks from attendance falls back safely on null`() {
        assertTrue(!marksFromAttendance(null).success)
        val marks = MarksRes(success = true, courses = listOf(MarksCourseItem(courseCode = "BACS3001")))
        assertEquals("BACS3001", marksFromAttendance(marks).marks.single().courseCode)
    }

    // ── D-091: bare Assignment[] -> LMSRes ──────────────────────────────────
    @Test
    fun `lms mapping handles day month alias from api array`() {
        val assignments = json.decodeFromString<List<MoodleAssignment>>(
            """[{"name":"BACS3001/OS/Assignment 1","due":"Sunday, 4 October 2026, 12:00 AM",
                 "done":false,"day":4,"month":10,"year":2026,"url":"https://lms/e/1",
                 "teachers":["Dr X"]}]"""
        )
        val lms = lmsFromMoodle(MoodleRes(success = true, data = assignments))
        val item = lms.assignments.single()
        assertEquals("BACS3001", item.courseCode)
        assertEquals("Assignment 1", item.assignmentTitle)
        assertEquals(4, item.day)
        assertEquals(10, item.month)
        assertEquals(2026, item.year)
        assertEquals("Dr X", item.teachers.single())
    }

    // ── D-099: /api/grades shape -> SemesterGradesRes ────────────────────────
    @Test
    fun `grades all shape maps requested semester into effective grades`() {
        val payload = """
            {"success":true,
             "overall":{"cgpa":{"cgpa":"8.85","creditsEarned":"120"},"curriculum":[]},
             "semesters":{"CH20262701":{"gpa":"9.1","grades":[
               {"slNo":"1","courseCode":"BACS3001","courseTitle":"OS","courseType":"Theory",
                "credits":"4","grade":"A","distributionType":"Regular","examMonth":"Nov-2026"}
             ]}}}
        """.trimIndent()
        val all = json.decodeFromString<AllGradesRes>(payload)
        val mapped = semesterGradesFromAll("CH20262701", all)
        assertTrue(mapped.success)
        val row = mapped.effectiveGrades!!.single()
        assertEquals("BACS3001", row.courseCode)
        assertEquals("A", row.grade)
        assertEquals("4", row.creditsEarned)
        assertEquals("1", all.semesters!!.getValue("CH20262701")!!.grades.single().slNo)
    }

    // ── D-011: DPoP-bound token uses the DPoP Authorization scheme ───────────
    @Test
    fun `dpop bound token uses DPoP authorization scheme and proof header`() {
        val headers = amazeAuthHeaders(
            token = "access-xyz",
            dpopHeader = "eyJhbGciOiJFZERTQSJ9.proof",
            confirmation = "eyJqdGkiOiJjbmYifQ"
        )
        assertEquals("DPoP access-xyz", headers["Authorization"])
        assertEquals("eyJhbGciOiJFZERTQSJ9.proof", headers["DPoP"])
    }

    @Test
    fun `plain token uses Bearer scheme and has no DPoP proof`() {
        val headers = amazeAuthHeaders(token = "access-plain", dpopHeader = null, confirmation = null)
        assertEquals("Bearer access-plain", headers["Authorization"])
        assertEquals(false, headers.containsKey("DPoP"))
        assertEquals(emptyMap<String, String>(), amazeAuthHeaders(null, "proof", "cnf"))
    }

    // ── D-104: hubs endpoint is guarded and decodes the envelope ─────────────
    @Test
    fun `cabshare hubs contract is an authenticated GET on the mounted path`() {
        val contract = cabshareHubsContract()
        assertEquals("/api/cabshare/hubs", contract.path)
        assertEquals("GET", contract.method)
        assertEquals(true, contract.authRequired)
    }

    @Test
    fun `cabshare hubs envelope decodes to typed hubs`() {
        val res = json.decodeFromString<CabShareHubsRes>(
            """{"success":true,"hubs":[{"hub_id":1,"hub_name":"VIT Chennai"},
                 {"hub_id":2,"hub_name":"Chennai Airport"}]}"""
        )
        assertEquals(2, res.hubs.size)
        assertEquals("Chennai Airport", res.hubs[1].hub_name)
    }

    // ── D-100: semester-shaped proxy payloads must not drop rows ─────────────
    @Test
    fun `wishlist semesters payload flattens tables and key values`() {
        val payload = """
            {"success":true,"semesters":{
              "CH20262701":{
                "tables":[{"headers":["Course","Title"],"rows":[
                  {"Course":"BACS3001","Title":"Operating Systems"}
                ]}],
                "keyValuePairs":{"Student":"21BCE1001","Semester":"Fall 2026"},
                "messages":["Wishlist closed"]
              }
            }}
        """.trimIndent()
        val res = arrearFromBulkResult(json.parseToJsonElement(payload))
        assertEquals(true, res.success)
        assertEquals(1, res.tables.size)
        assertEquals(listOf("Course", "Title"), res.tables.single().headers)
        assertEquals("BACS3001", res.tables.single().rows.single()[0])
        assertEquals("21BCE1001", res.keyValuePairs.single { it.label == "Student" }.value)
        assertEquals("Wishlist closed", res.messages.single().message)
    }

    @Test
    fun `course completion semesters payload flattens every semester`() {
        val payload = """
            {"success":true,"semesters":{
              "CH20262701":{"tables":[{"headers":["Course"],"rows":[{"Course":"A"}]}],
                            "keyValuePairs":{"Count":"1"}},
              "CH20262702":{"tables":[{"headers":["Course"],"rows":[{"Course":"B"}]}],
                            "keyValuePairs":{"Count":"2"}}
            }}
        """.trimIndent()
        val res = arrearFromBulkResult(json.parseToJsonElement(payload))
        assertEquals(2, res.tables.size)
        assertEquals(
            listOf("1", "2"),
            res.keyValuePairs.filter { it.label == "Count" }.map { it.value }
        )
    }

    // ── HTTP contract surface (path / method / body / auth) ──────────────────
    @Test
    fun `attendance ffcs and bulk contracts match the live mounts`() {
        val attendance = attendanceContract("CH20262701")
        assertEquals("/api/attendance", attendance.path)
        assertEquals("POST", attendance.method)
        assertEquals(mapOf("semesterId" to "CH20262701"), attendance.bodyFields)

        val ffcs = ffcsContract("qcm")
        assertEquals("/api/ffcs", ffcs.path)
        assertEquals("POST", ffcs.method)
        assertEquals(mapOf("section" to "qcm"), ffcs.bodyFields)
        assertEquals("feedback", ffcsContract("feedback", "CH20262701").bodyFields["section"])

        val bulk = bulkContract(listOf("hostel-counselling", "course-completion"))
        assertEquals("/api/bulk", bulk.path)
        assertEquals("POST", bulk.method)
        assertEquals("hostel-counselling,course-completion", bulk.bodyFields["endpoints"])
        assertEquals(false, bulk.authRequired)
    }

    @Test
    fun `guarded cabshare prefix contracts require auth with correct methods`() {
        val auth = cabshareGuardedContract("/api/cabshare/auth", "POST")
        val search = cabshareGuardedContract("/api/cabshare/trips", "GET")
        val create = cabshareGuardedContract("/api/cabshare/trips", "POST")
        val mine = cabshareGuardedContract("/api/cabshare/trips/me", "GET")
        val match = cabshareGuardedContract("/api/cabshare/match", "POST")
        listOf(auth, search, create, mine, match).forEach {
            assertEquals(true, it.authRequired)
            assertTrue(it.path.startsWith("/api/cabshare/"))
        }
        assertEquals("POST", auth.method)
        assertEquals("GET", search.method)
        assertEquals("POST", create.method)
        assertEquals("GET", mine.method)
        assertEquals("POST", match.method)
    }

    // ── D-108: curves body uses classId, not courseCode ──────────────────────
    @Test
    fun `curves body sends classId rather than courseCode`() {
        val params = curvesParams("CSE1001", "CH20262701")
        assertEquals("CSE1001", params["classId"])
        assertEquals("CH20262701", params["semesterId"])
        assertEquals(false, params.containsKey("courseCode"))
    }
}
