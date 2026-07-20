package com.amazecc.app.shared.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
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
data class NamedCalendar(
    val name: String,
    val months: List<CalendarMonth> = emptyList()
)

@Serializable
data class CalendarsListRes(
    val success: Boolean = true,
    val calendars: List<NamedCalendar> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QcmTable(
    val caption: String = "",
    @Contextual
    val rows: List<JsonElement> = emptyList()
)

@Serializable
data class QcmViewRes(
    val success: Boolean = true,
    val data: List<QcmTable>? = null,
    val message: String? = null,
    val error: String? = null
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
data class BusStop(
    val stopOrder: Int,
    val stopName: String,
    val pickupTime: String? = null
)

@Serializable
data class BusPlacement(
    val zone: String,
    val dispersalTime: String
)

@Serializable
data class BusRoute(
    val id: String,
    val type: String,
    val route: String,
    val boardingPoints: List<String> = emptyList(),
    val driverPhone: String,
    val driverName: String,
    val whatsappGroup: String,
    val busLocation: String,
    val supervisorName: String? = null,
    val supervisorPhone: String? = null,
    val driverInchargeName: String? = null,
    val driverInchargePhone: String? = null,
    val stops: List<BusStop> = emptyList(),
    val placements: List<BusPlacement> = emptyList()
)

@Serializable
data class BusesRes(
    val success: Boolean = true,
    val buses: List<BusRoute> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class TransportDataRes(
    val success: Boolean = true,
    val hasRegistration: Boolean = false,
    val registerNumber: String? = null,
    val name: String? = null,
    val programme: String? = null,
    val branch: String? = null,
    val routeSelected: String? = null,
    val fpReference: String? = null,
    val paymentStatus: String? = null,
    val busRouteId: String? = null,
    val qrCode: String? = null,
    val pageCsrf: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class TransportRegRequest(
    val routeNo: String,
    val semester: String,
    val studentName: String,
    val studentPhone: String
)

@Serializable
data class TransportRegSubmitRes(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val registrationId: String? = null
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
    @SerialName("club_id") val id: String? = null,
    @SerialName("club_name") val name: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null
)

@Serializable
data class ClubsRes(
    val success: Boolean = true,
    val clubs: List<ClubItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QBankCourse(val courseCode: String, val courseTitle: String)

@Serializable
data class QBankCoursesRes(
    val success: Boolean = true,
    val courses: List<QBankCourse> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class StudentProfile(
    @SerialName("applicationNumber") val regNo: String = "",
    val name: String = "",
    val email: String = "",
    @SerialName("mobileNumber") val mobile: String = "",
    @SerialName("appliedDegree") val program: String = "",
    val campus: String = "",
    val batch: String = "",
    val section: String? = null,
    val advisorName: String? = null,
    val bloodGroup: String? = null,
    val photoBase64: String? = null
)

@Serializable
data class StudentProfileRes(
    val success: Boolean = true,
    @SerialName("profile")
    val data: StudentProfile? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class FacultySchool(
    val id: String,
    val school_name: String
)

@Serializable
data class FacultySchoolsRes(
    val success: Boolean = true,
    val schools: List<FacultySchool> = emptyList(),
    val error: String? = null
)

@Serializable
data class FacultyProfile(
    val id: String,
    val name: String,
    val designation: String,
    val imageUrl: String = "",
    val profileUrl: String = "",
    val email: String = "",
    val employeeId: String = "",
    val intercom: String = ""
)

@Serializable
data class FacultyScrapeRes(
    val success: Boolean = true,
    val faculties: List<FacultyProfile> = emptyList(),
    val error: String? = null
)

@Serializable
data class CabTrip(
    val id: String,
    val from: String,
    val to: String,
    val date: String,
    val time: String,
    val seatsTotal: Int,
    val seatsAvailable: Int,
    val fare: String,
    val driverName: String,
    val driverPhone: String? = null,
    val driverRating: String? = null,
    val vehicleModel: String? = null,
    val vehicleColor: String? = null,
    val vehiclePlate: String? = null,
    val status: String = "Scheduled",
    val isOwnTrip: Boolean = false
)

@Serializable
data class CabTripsRes(
    val success: Boolean = true,
    val trips: List<CabTrip> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CabSearchRequest(
    val from: String,
    val to: String,
    val date: String
)

@Serializable
data class CabCreateTripRequest(
    val from: String,
    val to: String,
    val date: String,
    val time: String,
    val seats: Int,
    val fare: String,
    val vehicleModel: String? = null,
    val vehicleColor: String? = null,
    val vehiclePlate: String? = null
)

@Serializable
data class CabActionRes(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val tripId: String? = null
)

@Serializable
data class CabJoinRequest(
    val id: String,
    val tripId: String,
    val requesterName: String,
    val seats: Int,
    val status: String = "Pending"
)

@Serializable
data class CabJoinRequestsRes(
    val success: Boolean = true,
    val requests: List<CabJoinRequest> = emptyList(),
    val error: String? = null,
    val message: String? = null
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

@Serializable
data class ProfileImagesCredential(
    val account: String = "",
    val username: String = "",
    val defaultCredentials: String = "",
    val url: String? = null,
    val venueDate: String = "",
    val seatLocation: String = ""
)

@Serializable
data class ProfileImagesRank(
    val name: String = "",
    val rank: String = ""
)

@Serializable
data class ProfileImagesCredentials(
    val title: String = "",
    val credentials: List<ProfileImagesCredential> = emptyList(),
    val ranks: List<ProfileImagesRank> = emptyList()
)

@Serializable
data class ProfileImagesProctor(
    val title: String = "",
    val photoBase64: String? = null,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class ProfileImagesHodDeanPerson(
    val role: String = "",
    val details: Map<String, String> = emptyMap(),
    val photoBase64: String? = null
)

@Serializable
data class ProfileImagesHodDean(
    val title: String = "",
    val people: List<ProfileImagesHodDeanPerson> = emptyList()
)

@Serializable
data class ProfileImagesRes(
    val success: Boolean = true,
    val proctor: ProfileImagesProctor? = null,
    val hodDean: ProfileImagesHodDean? = null,
    val credentials: ProfileImagesCredentials? = null,
    val error: String? = null
)


@Serializable
data class CurriculumCategory(
    val code: String = "",
    val name: String = "",
    val credits: Int = 0,
    val maxCredits: Int = 0
)

@Serializable
data class CurriculumBasketItem(
    val code: String = "",
    val name: String = "",
    val credits: Int = 0,
    val type: String? = null
)

@Serializable
data class CurriculumBasket(
    val title: String = "",
    val credits: Int = 0,
    val items: List<CurriculumBasketItem> = emptyList()
)

@Serializable
data class CategoryDetail(
    val code: String = "",
    val name: String = "",
    val baskets: List<CurriculumBasket> = emptyList()
)

@Serializable
data class CurriculumRes(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val title: String = "",
    val totalCredits: Int = 0,
    val categories: List<CurriculumCategory> = emptyList(),
    val details: List<CategoryDetail> = emptyList()
)
