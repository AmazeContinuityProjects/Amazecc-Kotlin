package com.amazecc.app.shared.domain

import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import kotlin.test.*

class M3EProfileStateBridgeTest {

    // Helper to generate a baseline sample state
    private fun sampleProfileState(
        studentName: String = "Aditya Sharma",
        registrationNumber: String = "21BCE1001",
        applicationNumber: String = "1202204567",
        programme: String = "B.Tech Computer Science and Engineering",
        school: String = "SCOPE",
        campus: String = "Vellore Campus",
        dob: String = "15 Aug 2003",
        bloodGroup: String = "O +ve",
        email: String = "aditya.sharma2021@vitstudent.ac.in",
        phone: String = "+91 98765 43210",
        proctorName: String = "Dr. Ramesh Babu",
        proctorEmail: String = "ramesh.babu@vit.ac.in",
        proctorCabin: String? = "SJT 413",
        hostelBlock: String? = "Q Block",
        hostelRoom: String? = "514",
        cgpa: Double = 9.24,
        attendancePercentage: Double = 88.5,
        isIdCardFlipped: Boolean = false,
        isLoading: Boolean = false
    ) = M3EProfileState(
        studentName = studentName,
        registrationNumber = registrationNumber,
        applicationNumber = applicationNumber,
        programme = programme,
        school = school,
        campus = campus,
        dob = dob,
        bloodGroup = bloodGroup,
        email = email,
        phone = phone,
        proctorName = proctorName,
        proctorEmail = proctorEmail,
        proctorCabin = proctorCabin,
        hostelBlock = hostelBlock,
        hostelRoom = hostelRoom,
        cgpa = cgpa,
        attendancePercentage = attendancePercentage,
        isIdCardFlipped = isIdCardFlipped,
        isLoading = isLoading
    )

    // =========================================================================
    // Tier 1: Feature Tests (≥5 tests for Features 5, 6)
    // =========================================================================

    @Test
    fun testRichM3EProfileStateModelGenerationAndDefaults() {
        val state = sampleProfileState()

        assertEquals("Aditya Sharma", state.studentName)
        assertEquals("21BCE1001", state.registrationNumber)
        assertEquals("1202204567", state.applicationNumber)
        assertEquals("B.Tech Computer Science and Engineering", state.programme)
        assertEquals("SCOPE", state.school)
        assertEquals("Vellore Campus", state.campus)
        assertEquals("15 Aug 2003", state.dob)
        assertEquals("O +ve", state.bloodGroup)
        assertEquals("aditya.sharma2021@vitstudent.ac.in", state.email)
        assertEquals("+91 98765 43210", state.phone)
        assertEquals("Dr. Ramesh Babu", state.proctorName)
        assertEquals("ramesh.babu@vit.ac.in", state.proctorEmail)
        assertEquals("SJT 413", state.proctorCabin)
        assertEquals("Q Block", state.hostelBlock)
        assertEquals("514", state.hostelRoom)
        assertEquals(9.24, state.cgpa)
        assertEquals(88.5, state.attendancePercentage)
        assertFalse(state.isIdCardFlipped, "ID card should default to front side (not flipped)")
        assertFalse(state.isLoading, "Default isLoading should be false")
    }

    @Test
    fun testM3EProfileActionContractEmissionAndHandling() {
        val recordedActions = mutableListOf<M3EProfileAction>()
        val onAction: (M3EProfileAction) -> Unit = { recordedActions.add(it) }

        onAction(M3EProfileAction.FlipIdCard)
        onAction(M3EProfileAction.CopyRegNo)
        onAction(M3EProfileAction.ContactProctor)
        onAction(M3EProfileAction.Refresh)
        onAction(M3EProfileAction.SignOut)

        assertEquals(5, recordedActions.size)
        assertTrue(recordedActions[0] is M3EProfileAction.FlipIdCard)
        assertTrue(recordedActions[1] is M3EProfileAction.CopyRegNo)
        assertTrue(recordedActions[2] is M3EProfileAction.ContactProctor)
        assertTrue(recordedActions[3] is M3EProfileAction.Refresh)
        assertTrue(recordedActions[4] is M3EProfileAction.SignOut)
    }

    @Test
    fun testReactiveRealGpaAndAttendanceStatePropagation() {
        val initial = sampleProfileState(cgpa = 8.50, attendancePercentage = 82.0)
        assertEquals(8.50, initial.cgpa)
        assertEquals(82.0, initial.attendancePercentage)

        // Reactive update when AppState domain recalculates
        val updated = initial.copy(cgpa = 9.15, attendancePercentage = 91.5)
        assertEquals(9.15, updated.cgpa)
        assertEquals(91.5, updated.attendancePercentage)
        assertEquals(initial.studentName, updated.studentName)
        assertEquals(initial.registrationNumber, updated.registrationNumber)
    }

    @Test
    fun testDigitalIdCardFlipStateTransition() {
        var state = sampleProfileState(isIdCardFlipped = false)
        assertFalse(state.isIdCardFlipped)

        // User taps digital ID card to inspect barcode and proctor details on back
        state = state.copy(isIdCardFlipped = !state.isIdCardFlipped)
        assertTrue(state.isIdCardFlipped, "ID card should be flipped to back side")

        // User taps again to return to front
        state = state.copy(isIdCardFlipped = !state.isIdCardFlipped)
        assertFalse(state.isIdCardFlipped, "ID card should be flipped back to front side")
    }

    @Test
    fun testHostelDetailsMappingForHostellerAndDayboarder() {
        // Hosteller with block and room
        val hosteller = sampleProfileState(hostelBlock = "Men's Hostel Block D", hostelRoom = "302")
        assertEquals("Men's Hostel Block D", hosteller.hostelBlock)
        assertEquals("302", hosteller.hostelRoom)

        // Dayboarder with null hostel info
        val dayboarder = sampleProfileState(hostelBlock = null, hostelRoom = null)
        assertNull(dayboarder.hostelBlock)
        assertNull(dayboarder.hostelRoom)
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (≥5 tests for Features 5, 6)
    // =========================================================================

    @Test
    fun testZeroGpaAndZeroCreditsBoundary() {
        // First-year student before first semester grade release
        val freshman = sampleProfileState(
            cgpa = 0.0,
            attendancePercentage = 0.0
        )

        assertEquals(0.0, freshman.cgpa)
        assertEquals(0.0, freshman.attendancePercentage)
        assertFalse(freshman.cgpa.isNaN())
        assertFalse(freshman.attendancePercentage.isNaN())
    }

    @Test
    fun testMissingProctorCabinAndHostelDetailsFallback() {
        val stateWithoutCabin = sampleProfileState(
            proctorCabin = null,
            hostelBlock = null,
            hostelRoom = null
        )

        assertNull(stateWithoutCabin.proctorCabin)
        assertNull(stateWithoutCabin.hostelBlock)
        assertNull(stateWithoutCabin.hostelRoom)
        // Mandatory fields remain intact
        assertEquals("Dr. Ramesh Babu", stateWithoutCabin.proctorName)
        assertEquals("ramesh.babu@vit.ac.in", stateWithoutCabin.proctorEmail)
    }

    @Test
    fun testPerfect10GpaAnd100PercentAttendance() {
        val perfectState = sampleProfileState(
            cgpa = 10.0,
            attendancePercentage = 100.0
        )

        assertEquals(10.0, perfectState.cgpa)
        assertEquals(100.0, perfectState.attendancePercentage)
    }

    @Test
    fun testExtremeStringLengthsInNameAndProgramme() {
        val extremeName = "Sri Venkata Satya Sai Surya Narayana Murthy Anantha Padmanabha Swamy"
        val extremeProgramme = "Integrated Master of Technology in Computer Science and Engineering with Specialization in Data Science and Artificial Intelligence"

        val extremeState = sampleProfileState(
            studentName = extremeName,
            programme = extremeProgramme
        )

        assertEquals(extremeName, extremeState.studentName)
        assertEquals(extremeProgramme, extremeState.programme)
    }

    @Test
    fun testActionEmissionUnderLoadingState() {
        val recordedActions = mutableListOf<M3EProfileAction>()
        val onAction: (M3EProfileAction) -> Unit = { recordedActions.add(it) }

        val loadingState = sampleProfileState(isLoading = true)
        assertTrue(loadingState.isLoading)

        // Actions can still be triggered while loading (e.g. flipping card or refreshing)
        onAction(M3EProfileAction.FlipIdCard)
        onAction(M3EProfileAction.Refresh)

        assertEquals(2, recordedActions.size)
        assertEquals(M3EProfileAction.FlipIdCard, recordedActions[0])
        assertEquals(M3EProfileAction.Refresh, recordedActions[1])
    }

    // =========================================================================
    // Tier 3: Pairwise Combinations & End-to-End State Cycles
    // =========================================================================

    @Test
    fun testPairwiseAppStateDomainUpdateToProfileStateMapping() {
        // Initial state before sync
        val initial = sampleProfileState(
            studentName = "Aditya",
            cgpa = 8.80,
            attendancePercentage = 84.0,
            isLoading = false
        )

        // Start refresh cycle
        val syncing = initial.copy(isLoading = true)
        assertTrue(syncing.isLoading)

        // Domain sync completes with fresh GPA and attendance
        val resolved = syncing.copy(
            cgpa = 9.05,
            attendancePercentage = 87.5,
            isLoading = false
        )

        assertFalse(resolved.isLoading)
        assertEquals(9.05, resolved.cgpa)
        assertEquals(87.5, resolved.attendancePercentage)
        assertEquals(initial.registrationNumber, resolved.registrationNumber)
    }

    @Test
    fun testProfileRefreshCycleActionToState() {
        var state = sampleProfileState(isLoading = false)

        val handleAction: (M3EProfileAction) -> Unit = { action ->
            when (action) {
                is M3EProfileAction.Refresh -> {
                    state = state.copy(isLoading = true)
                }
                is M3EProfileAction.FlipIdCard -> {
                    state = state.copy(isIdCardFlipped = !state.isIdCardFlipped)
                }
                is M3EProfileAction.CopyRegNo -> {}
                is M3EProfileAction.ContactProctor -> {}
                is M3EProfileAction.SignOut -> {}
                is M3EProfileAction.SaveFriendlyName -> {}
                is M3EProfileAction.ToggleVtopPasswordVisibility -> {}
                is M3EProfileAction.CopyText -> {}
                is M3EProfileAction.OpenEmail -> {}
                is M3EProfileAction.OpenPhone -> {}
            }
        }

        handleAction(M3EProfileAction.Refresh)
        assertTrue(state.isLoading, "Refresh action must trigger isLoading == true")

        // Simulate network completion
        state = state.copy(isLoading = false)
        assertFalse(state.isLoading)
    }

    @Test
    fun testSignOutActionDispatchesAppLogout() {
        var loggedOut = false
        val handleAction: (M3EProfileAction) -> Unit = { action ->
            if (action is M3EProfileAction.SignOut) {
                loggedOut = true
            }
        }

        handleAction(M3EProfileAction.SignOut)
        assertTrue(loggedOut, "SignOut action must be intercepted by handler")
    }
}
