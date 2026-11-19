package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import com.amazecc.app.shared.domain.overhaul.M3EAcknowledgementDocument
import com.amazecc.app.shared.domain.overhaul.M3EAddressItem
import com.amazecc.app.shared.domain.overhaul.M3EContactItem
import com.amazecc.app.shared.domain.overhaul.M3EFieldItem
import com.amazecc.app.shared.domain.overhaul.M3EMentorItem
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.domain.overhaul.M3EResidenceStatus
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.IdentitySource
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme

/**
 * Stateful host for the Digital Student ID / Profile surface.
 *
 * Collects identity, domain, hostel, published grades and the reconciled residential choice,
 * maps them into an immutable [M3EProfileState], and hands every user event back through
 * [M3EProfileAction]. App.kt should route the profile destination here.
 */
@Composable
fun M3EProfileHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val identityState by UserStore.identity.collectAsState()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val hostelRes by AppState.hostelDetails.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    // AppState.residentialStatus is reconciled with the derived identity residence by the
    // data layer (identity.residenceStatus -> AppState.residentialStatus, onboarding as fallback).
    val residentialRaw by AppState.residentialStatus.collectAsState()
    val isBusSubscriber by AppState.isBusSubscriber.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var isFlipped by remember { mutableStateOf(false) }
    var showVtopPassword by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val residence = remember(identityState.residenceStatus, residentialRaw, identityState.isHosteller, isBusSubscriber) {
        val fromIdentity = parseM3EResidence(identityState.residenceStatus)
        val fromChoice = parseM3EResidence(residentialRaw)
        when {
            // A registered university bus forces dayscholar in the reference client and in
            // AppState's nav gate; the profile must agree with that reconciled decision.
            isBusSubscriber -> M3EResidenceStatus.DAYSCHOLAR
            fromIdentity != M3EResidenceStatus.UNKNOWN -> fromIdentity
            fromChoice != M3EResidenceStatus.UNKNOWN -> fromChoice
            // Legacy on-device hint, used only while both raw sources are absent/unrecognised.
            identityState.isHosteller -> M3EResidenceStatus.HOSTELLER
            else -> M3EResidenceStatus.UNKNOWN
        }
    }

    val contacts = remember(identityState.contacts) {
        identityState.contacts.map { c ->
            M3EContactItem(
                office = c.office,
                description = c.description,
                email = c.email?.takeIf { it.isNotBlank() }
            )
        }
    }

    val acknowledgementDocuments = remember(identityState.acknowledgement) {
        identityState.acknowledgement?.documents.orEmpty().map { d ->
            M3EAcknowledgementDocument(
                serialNo = d.serialNo.orEmpty(),
                documentName = d.documentName.orEmpty(),
                status = d.status.orEmpty()
            )
        }
    }

    val proctor = identityState.proctor
    val hostel = hostelRes?.hostelInfo
    val currentSem = domain.academics.semesters[selectedSemId]
    // Published CGPA from /api/remote/grades is authoritative; the selected-semester GPA
    // is only a fallback while grade history has not been fetched.
    val cgpa = allGrades?.publishedCgpa?.toDoubleOrNull()
        ?: currentSem?.gpa?.trim()?.toDoubleOrNull()
        ?: -1.0
    // Academics is the single source of truth for attendance; derive exactly like Home.
    val profileCourses = com.amazecc.app.shared.domain.Projections.mergedCourses(domain, selectedSemId)
    val profileTotalClasses = profileCourses.sumOf { it.attendance?.total ?: 0 }
    val profileAttendedClasses = profileCourses.sumOf { it.attendance?.attended ?: 0 }
    val attendancePct = if (profileTotalClasses > 0) {
        (profileAttendedClasses.toDouble() / profileTotalClasses) * 100.0
    } else -1.0

    val mentors = remember(identityState) {
        val list = mutableListOf<M3EMentorItem>()
        proctor?.let {
            list.add(
                M3EMentorItem(
                    role = "Proctor",
                    name = it.name ?: "",
                    designation = it.designation,
                    department = it.department ?: it.school,
                    email = it.email,
                    phone = it.phone,
                    cabin = it.cabin,
                    photoBase64 = it.photoBase64,
                    extras = it.extras.map { row -> M3EFieldItem(row.label, row.value) }
                )
            )
        }
        identityState.hodDean.forEach { official ->
            list.add(
                M3EMentorItem(
                    role = official.role ?: "Leadership",
                    name = official.name ?: "",
                    designation = official.designation,
                    department = official.department ?: official.school,
                    email = official.email,
                    phone = official.phone,
                    cabin = official.cabin,
                    photoBase64 = official.photoBase64,
                    extras = official.extras.map { row -> M3EFieldItem(row.label, row.value) }
                )
            )
        }
        list
    }

    val addresses = remember(identityState) {
        val list = mutableListOf<M3EAddressItem>()
        if (identityState.currentAddress.isNotEmpty()) {
            list.add(
                M3EAddressItem(
                    title = "Current Address",
                    lines = identityState.currentAddress.map { "${it.label}: ${it.value}" }
                )
            )
        }
        if (identityState.permanentAddress.isNotEmpty()) {
            list.add(
                M3EAddressItem(
                    title = "Permanent Address",
                    lines = identityState.permanentAddress.map { "${it.label}: ${it.value}" }
                )
            )
        }
        list
    }

    val personalRecords = remember(identityState) {
        listOfNotNull(
            identityState.nationality?.let { M3EFieldItem("Nationality", it) },
            identityState.nativeLanguage?.let { M3EFieldItem("Native Language", it) },
            identityState.nativeState?.let { M3EFieldItem("Native State", it) },
            identityState.community?.let { M3EFieldItem("Community", it) },
            identityState.religion?.let { M3EFieldItem("Religion", it) },
            identityState.caste?.let { M3EFieldItem("Caste", it) },
            identityState.physicallyChallenged?.let { M3EFieldItem("Physically Challenged", it) },
            identityState.aadharNumber?.let { M3EFieldItem("Aadhar Number", it) },
            identityState.friendMobile?.let { M3EFieldItem("Friend Mobile", it) },
            identityState.bloodGroup?.let { M3EFieldItem("Blood Group", it) }
        )
    }

    val vtopCred = identityState.credentials.firstOrNull { it.account.contains("VTOP", ignoreCase = true) }
    val vtopUser = vtopCred?.username ?: identityState.regNo ?: ""
    val vtopPass = vtopCred?.password ?: ""

    val state = M3EProfileState(
        // Empty values stay empty: placeholder text must never look like real student data.
        studentName = if (identityState.hasIdentity) identityState.displayName else "",
        registrationNumber = identityState.regNo ?: "",
        applicationNumber = identityState.applicationNumber ?: "",
        programme = identityState.program ?: "",
        school = identityState.school ?: identityState.proctor?.school ?: "",
        campus = identityState.campus ?: "",
        dob = identityState.dob ?: "",
        bloodGroup = identityState.bloodGroup ?: "",
        email = identityState.email ?: "",
        phone = identityState.mobile ?: "",
        proctorName = proctor?.name ?: "",
        proctorEmail = proctor?.email ?: "",
        proctorCabin = proctor?.cabin,
        hostelBlock = hostel?.blockName,
        hostelRoom = hostel?.roomNo,
        cgpa = cgpa,
        attendancePercentage = attendancePct,
        isIdCardFlipped = isFlipped,
        isLoading = isLoading,
        photoBase64 = identityState.photoBase64,
        friendlyName = identityState.friendlyName,
        proctorPhotoBase64 = proctor?.photoBase64,
        proctorPhone = proctor?.phone,
        proctorDepartment = proctor?.department ?: proctor?.school,
        proctorDesignation = proctor?.designation,
        bankInfo = identityState.bank?.let { b ->
            val accountNo = b.fields.firstOrNull {
                it.label.contains("account", ignoreCase = true) && it.label.contains("no", ignoreCase = true)
            }?.value ?: b.fields.firstOrNull { it.label.contains("account", ignoreCase = true) }?.value
            when {
                !b.name.isNullOrBlank() && !accountNo.isNullOrBlank() -> "${b.name} ($accountNo)"
                !b.name.isNullOrBlank() -> b.name
                !accountNo.isNullOrBlank() -> "Account $accountNo"
                else -> null
            }
        },
        apaarId = identityState.apaar?.let { a ->
            a.fields.firstOrNull {
                it.label.contains("apaar", ignoreCase = true) ||
                    it.label.contains("abc", ignoreCase = true) ||
                    it.label.contains("id", ignoreCase = true)
            }?.value ?: if (a.hasApaar) "Linked to Portal" else null
        },
        // Dayboarder info is its own whoami fragment: its presence is independent of residence.
        dayboarderInfo = identityState.dayboarder?.let { d ->
            val campusLabel = d.fields.firstOrNull { it.label.contains("campus", ignoreCase = true) }?.value
            val kind = if (d.isDayboarder) "Day Boarder" else "Resident"
            if (campusLabel.isNullOrBlank()) kind else "$kind ($campusLabel)"
        },
        eptStatus = when {
            identityState.eptTables.isNotEmpty() -> "${identityState.eptTables.size} Scheduled Exams"
            identityState.eptMessages.isNotEmpty() ->
                identityState.eptMessages.joinToString(" ") { it.value }.ifBlank { null }
            identityState.eptFields.isNotEmpty() ->
                identityState.eptFields.joinToString(" • ") { "${it.label}: ${it.value}" }.ifBlank { null }
            else -> null
        },
        rankAir = identityState.ranks.firstOrNull()?.rank?.takeIf { it.isNotBlank() }?.let { "AIR $it" },
        registrationSchedule = identityState.registrationTables.firstOrNull()?.caption
            ?: identityState.registrationFields.firstOrNull()?.value,
        vtopUsername = vtopUser,
        vtopPassword = vtopPass,
        showVtopPassword = showVtopPassword,
        mentors = mentors,
        addresses = addresses,
        personalRecords = personalRecords,
        residence = residence,
        contacts = contacts,
        acknowledgementDocuments = acknowledgementDocuments,
        isRegisteredForBus = isBusSubscriber
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EProfileScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EProfileAction.FlipIdCard -> isFlipped = !isFlipped
                    is M3EProfileAction.CopyRegNo -> {
                        clipboardManager.setText(AnnotatedString(state.registrationNumber))
                    }
                    is M3EProfileAction.ContactProctor -> {
                        if (state.proctorEmail.isNotBlank()) {
                            try { uriHandler.openUri("mailto:${state.proctorEmail}") } catch (_: Exception) {}
                        }
                    }
                    is M3EProfileAction.SignOut -> AppState.logout()
                    is M3EProfileAction.Refresh -> AppState.refreshProfile()
                    is M3EProfileAction.SaveFriendlyName -> {
                        SettingsManager.setString("friendly_name", action.name)
                        UserStore.merge(
                            StudentIdentity(friendlyName = action.name),
                            IdentitySource.SESSION
                        )
                    }
                    is M3EProfileAction.ToggleVtopPasswordVisibility -> showVtopPassword = !showVtopPassword
                    is M3EProfileAction.CopyText -> {
                        clipboardManager.setText(AnnotatedString(action.text))
                    }
                    is M3EProfileAction.OpenEmail -> {
                        try { uriHandler.openUri("mailto:${action.email}") } catch (_: Exception) {}
                    }
                    is M3EProfileAction.OpenPhone -> {
                        try { uriHandler.openUri("tel:${action.phone}") } catch (_: Exception) {}
                    }
                    is M3EProfileAction.SetResidence -> {
                        val raw = when (action.residence) {
                            M3EResidenceStatus.HOSTELLER -> "hosteller"
                            M3EResidenceStatus.DAYSCHOLAR -> "dayscholar"
                            M3EResidenceStatus.UNKNOWN -> null
                        }
                        if (raw != null) AppState.setResidentialStatus(raw)
                    }
                    is M3EProfileAction.SetRegisteredForBus -> {
                        AppState.setBusSubscriber(action.enabled)
                    }
                }
            },
            modifier = modifier
        )
    }
}

/** Tolerant parse of the reconciled residential choice ("HOSTELLER"/"DAYSCHOLAR" or onboarding text). */
internal fun parseM3EResidence(raw: String?): M3EResidenceStatus = when (raw?.trim()?.uppercase()) {
    "HOSTELLER", "HOSTEL", "HOSTELER" -> M3EResidenceStatus.HOSTELLER
    "DAYSCHOLAR", "DAY SCHOLAR", "DAY_SCHOLAR", "DAYSCHOLAR", "DAY BOARDER" -> M3EResidenceStatus.DAYSCHOLAR
    else -> M3EResidenceStatus.UNKNOWN
}
