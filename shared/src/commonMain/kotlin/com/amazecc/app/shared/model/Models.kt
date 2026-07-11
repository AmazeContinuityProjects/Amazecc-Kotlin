package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val cookies: String? = null,
    val csrf: String? = null,
    val authorizedID: String? = null,
    val clubToken: String? = null,
    val clubRoles: List<ClubRole> = emptyList(),
    val error: String? = null
)

@Serializable
data class ClubRole(
    val club_id: String,
    val role: String
)

@Serializable
data class CourseItem(
    val slNo: String,
    val course: String,
    val courseCode: String,
    val LTPJC: String? = null,
    val category: String? = null,
    val classId: String,
    val slotVenue: String? = null,
    val facultyDetails: String? = null
)

@Serializable
data class DetailedAttendance(
    val date: String,
    val status: String
)

@Serializable
data class AttendanceItem(
    val slNo: String? = null,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String? = null,
    val slotName: String,
    val faculty: String? = null,
    val registrationDate: String? = null,
    val attendanceDate: String? = null,
    val attendedClasses: Int? = null,
    val totalClasses: Int? = null,
    val attendancePercentage: String? = null,
    val slotVenue: String? = null,
    val classId: String? = null,
    val credits: String? = null,
    val category: String? = null
)

@Serializable
data class AttendanceRes(
    val success: Boolean = true,
    val semester: String? = null,
    val semesterId: String? = null,
    val attendance: List<AttendanceItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class TimetableRes(
    val success: Boolean = true,
    val semesterId: String? = null,
    val courseInfo: List<CourseItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class AssessmentItem(
    val slNo: String,
    val title: String,
    val maxMark: String,
    val weightagePercent: String,
    val status: String,
    val scoredMark: String,
    val weightageMark: String
)

@Serializable
data class MarksCourseItem(
    val slNo: String,
    val classNbr: String,
    val courseCode: String,
    val credits: Double,
    val courseTitle: String,
    val courseType: String,
    val courseSystem: String? = null,
    val faculty: String,
    val slot: String,
    val courseMode: String? = null,
    val assessments: List<AssessmentItem> = emptyList()
)

@Serializable
data class CGPAResult(
    val creditsRequired: String? = null,
    val creditsEarned: String? = null,
    val cgpa: String? = null,
    val nonGradedRequirement: String? = null
)

@Serializable
data class MarksRes(
    val success: Boolean = true,
    val marks: List<MarksCourseItem> = emptyList(),
    val cgpa: CGPAResult? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class GradeBreakdown(
    val slNo: String,
    val component: String,
    val maxMark: String,
    val weightagePercent: String,
    val status: String,
    val scoredMark: String,
    val weightageMark: String
)

@Serializable
data class GradeRange(
    val S: String,
    val A: String,
    val B: String,
    val C: String,
    val D: String,
    val E: String,
    val F: String
)

@Serializable
data class GradeItem(
    val slNo: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val grandTotal: String,
    val grade: String,
    val courseId: String? = null,
    val details: List<GradeBreakdown>? = null,
    val range: GradeRange? = null
)

@Serializable
data class SemesterGradeResult(
    val gpa: String? = null,
    val grades: List<GradeItem> = emptyList()
)

@Serializable
data class AllGradesRes(
    val success: Boolean = true,
    val semesterId: String? = null,
    val grades: Map<String, SemesterGradeResult> = emptyMap(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class HostelDetails(
    val success: Boolean = true,
    val gender: String? = null,
    val isHosteller: Boolean = false,
    val blockName: String? = null,
    val roomNo: String? = null,
    val messInfo: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class LeaveItem(
    val leaveId: String,
    val visitPlace: String? = null,
    val reason: String? = null,
    val leaveType: String? = null,
    val from: String? = null,
    val to: String? = null,
    val status: String? = null,
    val remarks: String? = null
)

@Serializable
data class HostelLeaveRes(
    val success: Boolean = true,
    val leaves: List<LeaveItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ExamItem(
    val courseCode: String,
    val courseTitle: String,
    val classId: String,
    val slot: String,
    val examDate: String,
    val examSession: String,
    val reportingTime: String,
    val examTime: String,
    val venue: String,
    val seatLocation: String,
    val seatNo: String
)

@Serializable
data class ExamScheduleRes(
    val success: Boolean = true,
    val schedule: Map<String, List<ExamItem>> = emptyMap(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CalendarEvent(
    val type: String, // "Instructional Day", "Holiday", "Other"
    val text: String,
    val color: String? = null,
    val category: String? = null
)

@Serializable
data class CalendarDay(
    val date: Int,
    val events: List<CalendarEvent> = emptyList()
)

@Serializable
data class CalendarMonth(
    val month: String,
    val days: List<CalendarDay> = emptyList()
)

@Serializable
data class CalendarRes(
    val success: Boolean = true,
    val months: List<CalendarMonth> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class PaymentItem(
    val billingId: String,
    val description: String,
    val amount: String,
    val dueDate: String? = null,
    val status: String,
    val paymentDate: String? = null,
    val receiptNo: String? = null
)

@Serializable
data class PaymentsRes(
    val success: Boolean = true,
    val payments: List<PaymentItem> = emptyList(),
    val walletBalance: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class BookItem(
    val bookId: String,
    val title: String,
    val author: String? = null,
    val issueDate: String? = null,
    val dueDate: String? = null,
    val fineAmount: String? = null
)

@Serializable
data class LibraryRes(
    val success: Boolean = true,
    val booksIssued: List<BookItem> = emptyList(),
    val searchResults: List<BookItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class BusItem(
    val routeNo: String,
    val routeName: String,
    val time: String,
    val driverName: String? = null,
    val driverPhone: String? = null
)

@Serializable
data class TransportRes(
    val success: Boolean = true,
    val buses: List<BusItem> = emptyList(),
    val dayBoarderStatus: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class LMSAssignment(
    val assignmentId: String,
    val courseCode: String,
    val title: String,
    val maxMarks: String,
    val dueDate: String,
    val status: String, // "Submitted", "Pending"
    val score: String? = null
)

@Serializable
data class LMSRes(
    val success: Boolean = true,
    val assignments: List<LMSAssignment> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QBankQuestion(
    val question_id: String,
    val question_text: String,
    val question_type: String,
    val options: Map<String, String>? = null,
    val correct_answer: String? = null,
    val marks: Int? = null,
    val topic_name: String? = null,
    val exam_semester: String? = null,
    val exam_year: String? = null,
    val image_url: String? = null
)

@Serializable
data class QBankRes(
    val success: Boolean = true,
    val data: List<QBankQuestion> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class EventHubEvent(
    val eid: String,
    val title: String,
    val eligibility: String? = null,
    val type: String? = null,
    val date: String? = null,
    val location: String? = null,
    val price: String? = null,
    val time: String? = null,
    val isPastEvent: Boolean = false
)

@Serializable
data class EventHubRes(
    val success: Boolean = true,
    val events: List<EventHubEvent> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ClubItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null
)

@Serializable
data class ClubsRes(
    val success: Boolean = true,
    val clubs: List<ClubItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)
