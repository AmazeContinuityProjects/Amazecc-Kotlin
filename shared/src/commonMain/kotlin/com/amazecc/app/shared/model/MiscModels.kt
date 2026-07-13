package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
data class TimetableRes(
    val success: Boolean = true,
    val semesterId: String? = null,
    val courseInfo: List<CourseItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
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
data class HostelLeaveRes(
    val success: Boolean = true,
    val leaves: List<LeaveItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ExamScheduleRes(
    val success: Boolean = true,
    val schedule: Map<String, List<ExamItem>> = emptyMap(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CalendarRes(
    val success: Boolean = true,
    val months: List<CalendarMonth> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class MoodleAssignment(
    val name: String,
    val due: String,
    val done: Boolean = false,
    val url: String? = null,
    val teachers: List<String> = emptyList(),
    val hidden: Boolean = false
)

@Serializable
data class MoodleRes(
    val success: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val data: List<MoodleAssignment> = emptyList()
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
    val total: Int = 0,
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
data class EventHubRes(
    val success: Boolean = true,
    val events: List<EventHubEvent> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ClubItem(
    val id: String? = null,
    val name: String? = null,
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

@Serializable
data class StudentProfile(
    val regNo: String = "",
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val program: String = "",
    val campus: String = "",
    val batch: String = "",
    val section: String? = null,
    val advisorName: String? = null,
    val bloodGroup: String? = null
)

@Serializable
data class StudentProfileRes(
    val success: Boolean = true,
    val data: StudentProfile? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class VitolData(
    val balance: String,
    val limit: String,
    val consumed: String,
    val message: String
)

@Serializable
data class VitolRes(
    val success: Boolean = true,
    val data: VitolData? = null,
    val message: String? = null,
    val error: String? = null
)

