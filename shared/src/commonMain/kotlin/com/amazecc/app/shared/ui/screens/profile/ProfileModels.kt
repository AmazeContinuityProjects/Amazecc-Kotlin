package com.amazecc.app.shared.ui.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ProfileGroup(val label: String) {
    IDENTITY("Identity"),
    RECORDS("Records & Registration"),
    ACHIEVEMENTS("Achievements"),
    GENERAL("General")
}

enum class ProfileSubScreen(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val group: ProfileGroup
) {
    PERSONAL_INFO("Personal Information", "Identity, contact & residence details", Icons.Rounded.Person, ProfileGroup.IDENTITY),
    ACADEMIC_DETAILS("Academic & Campus Details", "Program, section, advisor & more", Icons.Rounded.School, ProfileGroup.IDENTITY),
    UNIVERSITY_OFFICIALS("University Officials", "Proctor & HoD/Dean contacts", Icons.Rounded.SupportAgent, ProfileGroup.IDENTITY),
    EPT_SCHEDULE("EPT Schedule", "Extra practice test schedule", Icons.Rounded.Event, ProfileGroup.RECORDS),
    REGISTRATION("Registration Schedule", "Course registration windows", Icons.Rounded.HowToReg, ProfileGroup.RECORDS),
    UNIVERSITY_DAY("University Day", "Awards & certificates of merit", Icons.Rounded.WorkspacePremium, ProfileGroup.RECORDS),
    BANK_DETAILS("Bank Details", "Saved bank / scholarship account info", Icons.Rounded.AccountBalance, ProfileGroup.RECORDS),
    DAYBOARDER("Dayboarder", "Transport & day scholar details", Icons.Rounded.Commute, ProfileGroup.RECORDS),
    APAAR_ID("APAAR ID", "Automated Permanent Academic Account Registry", Icons.Rounded.Badge, ProfileGroup.RECORDS),
    CREDENTIALS("Credentials & Ranks", "VITEEE rank & linked account credentials", Icons.Rounded.EmojiEvents, ProfileGroup.ACHIEVEMENTS)
}
