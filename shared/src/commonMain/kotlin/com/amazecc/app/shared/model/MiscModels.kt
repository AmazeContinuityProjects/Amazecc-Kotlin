package com.amazecc.app.shared.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
data class BasicRes(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val cookies: String? = null,
    val csrf: String? = null,
    val authorizedID: String? = null,
    val clubToken: String? = null,
    val error: String? = null
)

@Serializable
data class TimetableRes(
    val success: Boolean = true,
    val semesterId: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CGPAResult(
    val creditsEarned: String? = null,
    val cgpa: String? = null
)

@Serializable
data class MarksRes(
    val success: Boolean = true,
    val marks: List<MarksCourseItem> = emptyList(),
    val cgpa: CGPAResult? = null,
    val error: String? = null,
    val message: String? = null
)

val MarksRes?.displayCgpa: Double get() = this?.cgpa?.cgpa?.toDoubleOrNull() ?: 0.0
val MarksRes?.displayCreditsEarned: Double get() = this?.cgpa?.creditsEarned?.toDoubleOrNull() ?: 0.0

@Serializable
data class HostelInfo(
    val gender: String? = null,
    val isHosteller: Boolean = false,
    val blockName: String? = null,
    val roomNo: String? = null,
    val messInfo: String? = null
)

@Serializable
data class HostelDetails(
    val success: Boolean = true,
    val hostelInfo: HostelInfo? = null,
    val leaveHistory: List<LeaveItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ExamScheduleRes(
    val success: Boolean = true,
    @kotlinx.serialization.SerialName("Schedule") val rawScheduleUpper: Map<String, List<ExamItem>>? = null,
    @kotlinx.serialization.SerialName("schedule") val rawScheduleLower: Map<String, List<ExamItem>>? = null,
    val error: String? = null,
    val message: String? = null
) {
    val schedule: Map<String, List<ExamItem>>
        get() = rawScheduleUpper ?: rawScheduleLower ?: emptyMap()
}

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
    @Contextual
    val data: JsonElement? = null,
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
) {
    val courseCode: String get() {
        val parts = name.split("/")
        val first = parts.firstOrNull()?.trim() ?: return ""
        val code = first.split("-").firstOrNull()?.trim() ?: first
        val match = Regex("[A-Za-z]+\\d+").find(code)
        return match?.value ?: code.take(20)
    }

    val courseTitle: String get() {
        val parts = name.split("/")
        return if (parts.size >= 2) parts[1].trim() else name
    }

    val taskTitle: String get() {
        val parts = name.split("/")
        return if (parts.size >= 3) parts.drop(2).joinToString("/").trim() else name
    }
}

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
    val stops: List<BusStop> = emptyList()
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
    val branch: String? = null,
    val routeSelected: String? = null,
    val busRouteId: String? = null,
    val qrCode: String? = null,
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
    val exam_semester: String? = null
)

@Serializable
data class QBankRes(
    val success: Boolean = true,
    val data: List<QBankQuestion> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QBankPaper(
    val title: String,
    val link: String,
    val type: String
)

@Serializable
data class QBankPapersRes(
    val success: Boolean = true,
    val data: List<QBankPaper> = emptyList(),
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
    @SerialName("logo_url") val logoUrl: String? = null,
    val website: String? = null,
    val instagram: String? = null,
    val whatsapp: String? = null
)

@Serializable
data class ClubsRes(
    val success: Boolean = true,
    val clubs: List<ClubItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class FeedLink(val title: String = "", val url: String = "")

@Serializable
data class FeedPost(
    val id: String = "",
    @SerialName("club_id") val clubId: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val content: String = "",
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("has_promoted") val hasPromoted: Boolean = false,
    @SerialName("promote_count") val promoteCount: Int = 0,
    val links: List<FeedLink>? = null
)

@Serializable
data class FeedRes(
    val success: Boolean = true,
    val feed: List<FeedPost> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QBankSubmitRes(val success: Boolean, val message: String? = null)

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
    @SerialName("image") val photoBase64: String? = null,
    val nativeLanguage: String? = null,
    val nationality: String? = null,
    val community: String? = null,
    val religion: String? = null,
    val caste: String? = null,
    val physicallyChallenged: String? = null,
    val aadharNumber: String? = null
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
data class DirectoryCCProfile(
    val employeeId: String = "",
    val name: String = "",
    val designation: String = "",
    val email: String = "",
    val intercom: String = ""
)

@Serializable
data class CabShareUser(
    val reg_number: String,
    val name: String = "",
    val phone_number: String = ""
)

@Serializable
data class CabShareHub(
    val hub_id: Int,
    val hub_name: String
)

val fallbackCabHubs = listOf(
    CabShareHub(1, "VIT Chennai"),
    CabShareHub(2, "Chennai Airport"),
    CabShareHub(3, "Chennai Central Railway Station"),
    CabShareHub(4, "Tambaram Railway Station"),
    CabShareHub(5, "Chengalpattu Railway Station"),
    CabShareHub(6, "Koyambedu Bus Stand"),
    CabShareHub(7, "Kelambakkam"),
    CabShareHub(8, "Sholinganallur"),
    CabShareHub(9, "T Nagar"),
    CabShareHub(10, "Guindy"),
    CabShareHub(11, "OMR"),
    CabShareHub(12, "Perungudi"),
    CabShareHub(13, "Thoraipakkam"),
    CabShareHub(14, "Velachery")
)

@Serializable
data class CabShareTrip(
    val trip_id: Long = 0,
    val reg_number: String = "",
    val name: String = "",
    val owner_name: String = "",
    val owner_phone: String = "",
    val from_hub_name: String = "",
    val hub_id: Int? = null,
    val hub_name: String = "",
    val travel_date: String = "",
    val preferred_time: String = "",
    val tolerance_hours: Double = 1.0,
    val notes: String = "",
    val status: String = "active",
    val match_status: String? = null,
    val requests: List<CabShareMatchRequest> = emptyList()
)

@Serializable
data class CabShareMatchRequest(
    val match_id: Long = 0,
    val name: String = "",
    val phone_number: String = "",
    val status: String = "pending"
)

@Serializable
data class CabShareTripsRes(
    val success: Boolean = true,
    val trips: List<CabShareTrip> = emptyList(),
    val my_trips: List<CabShareTrip> = emptyList(),
    val joined_trips: List<CabShareTrip> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CabShareAuthRes(
    val success: Boolean = true,
    val user: CabShareUser? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CabActionRes(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val tripId: String? = null
)


@Serializable
data class ProfileImagesCredential(
    val account: String = "",
    val username: String = "",
    val url: String? = null
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
data class ProfileImagesStudent(
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
    val student: ProfileImagesStudent? = null,
    val profile: ProfileImagesStudent? = null,
    val studentPhoto: String? = null,
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

@Serializable
data class EptScheduleRes(
    val success: Boolean = true,
    @Contextual
    val tables: List<JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class RegistrationScheduleRes(
    val success: Boolean = true,
    @Contextual
    val tables: List<JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class BankInfoRes(
    val success: Boolean = true,
    @Contextual
    val bankDetails: JsonElement? = null,
    @Contextual
    val fields: Map<String, JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class DayboarderRes(
    val success: Boolean = true,
    @Contextual
    val fields: Map<String, JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class ApaarIdRes(
    val success: Boolean = true,
    val hasApaar: Boolean = false,
    @Contextual
    val formFields: Map<String, JsonElement>? = null,
    @Contextual
    val keyValuePairs: Map<String, JsonElement>? = null,
    @Contextual
    val tables: List<JsonElement>? = null,
    val error: String? = null
)
