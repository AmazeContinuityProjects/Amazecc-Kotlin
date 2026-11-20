package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Immutable
import com.amazecc.app.shared.model.RESIDENCE_DAYSCHOLAR
import com.amazecc.app.shared.model.RESIDENCE_HOSTELLER
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.ui.screens.more.appLibraryItems

/**
 * D-053: the fresher welcome is driven by a real content source instead of
 * hand-written prose.
 *
 * The source is [appLibraryItems] — the same single source of truth the App
 * Library sheet and command palette use. Each section title and description is
 * the module's real label/subLabel, so the guide cannot drift away from what the
 * app actually ships. The only variable input is the student's real profile
 * state (name + residence), which decides which modules are featured.
 */
@Immutable
data class M3EFresherProfile(
    val displayName: String = "",
    val isHosteller: Boolean = false,
    val isDayScholar: Boolean = false
) {
    val hasKnownResidence: Boolean get() = isHosteller || isDayScholar
}

object M3EFresherContent {

    /** Build the key from real identity data (whoami-derived). */
    fun fromIdentity(identity: StudentIdentity): M3EFresherProfile = M3EFresherProfile(
        displayName = identity.displayName,
        isHosteller = identity.isHosteller || identity.residenceStatus == RESIDENCE_HOSTELLER,
        isDayScholar = identity.residenceStatus == RESIDENCE_DAYSCHOLAR
    )

    /**
     * Resolve each orientation category to a real registered module. A category
     * whose module is missing from the registry is omitted rather than filled
     * with invented copy.
     */
    fun sectionsFor(profile: M3EFresherProfile): List<M3EFresherSection> {
        val wanted = linkedMapOf<M3EFresherCategory, String>()
        wanted[M3EFresherCategory.CAMPUS] = "Libraries"
        wanted[M3EFresherCategory.ACADEMICS] = "Academics Hub"
        // Unknown residence still gets the hostel guide; a known day-scholar
        // profile does not. This is the "keyed by fresher state" part.
        if (profile.isHosteller || !profile.hasKnownResidence) {
            wanted[M3EFresherCategory.HOSTEL] = "Hostel Hub"
        }
        wanted[M3EFresherCategory.TRANSPORT] = "Transport"
        wanted[M3EFresherCategory.EVENTS] = "Event Hub"

        return wanted.mapNotNull { (category, moduleLabel) ->
            appLibraryItems.firstOrNull { it.label == moduleLabel }?.let { module ->
                M3EFresherSection(
                    category = category,
                    title = module.label,
                    description = module.subLabel
                )
            }
        }
    }

    /** Real-name greeting; falls back to the campus welcome when unknown. */
    fun welcomeTitle(profile: M3EFresherProfile): String =
        if (profile.displayName.isBlank()) "Welcome to VIT!" else "Welcome, ${profile.displayName}!"

    /** Subtitle derived from the resolved registry, not fabricated. */
    fun welcomeSubtitle(sections: List<M3EFresherSection>): String =
        "${sections.size} starter ${if (sections.size == 1) "module" else "modules"} from your app library"
}
