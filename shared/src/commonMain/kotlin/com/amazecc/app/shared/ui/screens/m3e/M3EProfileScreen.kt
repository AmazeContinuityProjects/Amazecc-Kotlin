package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EAddressItem
import com.amazecc.app.shared.domain.overhaul.M3EFieldItem
import com.amazecc.app.shared.domain.overhaul.M3EMentorItem
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Digital Student ID & Profile Screen.
 * 100% Stateless: accepts [M3EProfileState] and emits [M3EProfileAction].
 * Features double-sided Digital ID card with photo decoding, status cards, proctor/mentor cards,
 * personal records, addresses, and VTOP credentials.
 */
@Composable
fun M3EProfileScreen(
    state: M3EProfileState,
    onAction: (M3EProfileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Student Profile",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Identity & Academic Credentials",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onAction(M3EProfileAction.Refresh) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerLow)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3EProfileAction.SignOut) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.errorContainer)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign Out",
                            tint = colors.onError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Digital Student ID Card
        item {
            DigitalIdCard(
                state = state,
                onFlip = { onAction(M3EProfileAction.FlipIdCard) },
                onCopyRegNo = { onAction(M3EProfileAction.CopyRegNo) }
            )
        }

        // 3. Quick Academic Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    title = "CGPA",
                    value = profileMetricValue(state.cgpa),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "ATTENDANCE",
                    value = profileMetricValue(state.attendancePercentage, "%"),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "CAMPUS",
                    value = orDash(state.campus),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Status Cards Grid
        val statusCards = listOfNotNull(
            state.bankInfo?.let { Triple("Bank Details", it, Icons.Rounded.AccountBalance) },
            state.apaarId?.let { Triple("APAAR / ABC ID", it, Icons.Rounded.Badge) },
            state.dayboarderInfo?.let { Triple("Campus Residence", it, Icons.Rounded.Home) },
            state.eptStatus?.let { Triple("EPT Schedule", it, Icons.Rounded.School) },
            state.registrationSchedule?.let { Triple("Registration", it, Icons.Rounded.HowToReg) },
            state.rankAir?.let { Triple("Entrance Rank", it, Icons.Rounded.EmojiEvents) }
        )
        if (statusCards.isNotEmpty()) {
            item {
                StatusCardsSection(statusCards = statusCards)
            }
        }

        // 5. Proctor Contact Card
        item {
            ProctorContactCard(
                state = state,
                onContact = { onAction(M3EProfileAction.ContactProctor) },
                onPhone = { state.proctorPhone?.let { onAction(M3EProfileAction.OpenPhone(it)) } }
            )
        }

        // 6. Faculty Mentors & Leadership Cards (if any HoD/Dean mentors exist)
        val leadershipMentors = state.mentors.filter { it.role != "Proctor" }
        if (leadershipMentors.isNotEmpty()) {
            item {
                MentorsSection(
                    mentors = leadershipMentors,
                    onEmail = { onAction(M3EProfileAction.OpenEmail(it)) },
                    onPhone = { onAction(M3EProfileAction.OpenPhone(it)) }
                )
            }
        }

        // 7. Personal Information Card
        item {
            PersonalDetailsCard(state = state)
        }

        // 8. Detailed Personal Records Grid (if available)
        if (state.personalRecords.isNotEmpty()) {
            item {
                PersonalRecordsSection(records = state.personalRecords)
            }
        }

        // 9. Address Records (Current & Permanent)
        if (state.addresses.isNotEmpty()) {
            item {
                AddressRecordsSection(addresses = state.addresses)
            }
        }

        // 10. VTOP Credentials Card
        if (!state.vtopUsername.isNullOrBlank()) {
            item {
                CredentialsCard(
                    username = state.vtopUsername,
                    password = state.vtopPassword ?: "",
                    showPassword = state.showVtopPassword,
                    onToggleVisibility = { onAction(M3EProfileAction.ToggleVtopPasswordVisibility) },
                    onCopy = { text, label -> onAction(M3EProfileAction.CopyText(text, label)) }
                )
            }
        }
    }
}

@Composable
private fun DigitalIdCard(
    state: M3EProfileState,
    onFlip: () -> Unit,
    onCopyRegNo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    val photoBitmap = remember(state.photoBase64) {
        state.photoBase64?.let { b64 ->
            try {
                val clean = if (b64.contains(",")) b64.substringAfter(",") else b64
                clean.trim().decodeBase64Bytes().toImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onFlip)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Card Top Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "VELLORE INSTITUTE OF TECHNOLOGY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = colors.primary
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.FlipCameraAndroid,
                    contentDescription = "Flip Card",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!state.isIdCardFlipped) {
                // Front Side: Photo, Name, Reg No, Barcode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap,
                                contentDescription = "Student Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = orDash(state.studentName),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = colors.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = orDash(state.registrationNumber),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.primary
                            )

                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Reg No",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable(onClick = onCopyRegNo)
                            )
                        }

                        Text(
                            text = listOf(state.programme, state.school).filter { it.isNotBlank() }
                                .joinToString(" • ").ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Library Barcode Visual Simulation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerLowest)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Simulated Barcode Pattern
                        repeat(32) { index ->
                            val barWidth = if (index % 3 == 0) 3.dp else 1.5.dp
                            val barHeight = if (index % 5 == 0) 34.dp else 28.dp
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .height(barHeight)
                                    .background(colors.onSurface)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "*${state.registrationNumber}*",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                // Back Side: Residence, Emergency, Blood Group, QR
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "EMERGENCY & RESIDENCE INFO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = colors.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Blood Group",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = state.bloodGroup,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                        }

                        Column {
                            Text(
                                text = "Date of Birth",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = state.dob,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                        }

                        Column {
                            Text(
                                text = "App No",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = state.applicationNumber,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                        }
                    }

                    if (state.hostelBlock != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Hostel Residence",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = "${state.hostelBlock} - Room ${orDash(state.hostelRoom ?: "")}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceContainerLowest)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Digital ID Signature",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "Verified VIT Smart Card",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "TAP TO FLIP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 16.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.primary
            )
        }
    }
}

@Composable
private fun StatusCardsSection(
    statusCards: List<Triple<String, String, ImageVector>>,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Academic & Campus Status",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface
        )

        val pairs = statusCards.chunked(2)
        pairs.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { (title, subtitle, icon) ->
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 16.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProctorContactCard(
    state: M3EProfileState,
    onContact: () -> Unit,
    onPhone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Faculty Proctor",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = orDash(state.proctorName),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = orDash(state.proctorEmail),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    if (!state.proctorCabin.isNullOrBlank()) {
                        Text(
                            text = "Cabin: ${state.proctorCabin}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                    }
                    if (!state.proctorDepartment.isNullOrBlank()) {
                        Text(
                            text = "Dept: ${state.proctorDepartment}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.proctorPhone.isNullOrBlank()) {
                        IconButton(
                            onClick = onPhone,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = "Call Proctor",
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    ElevatedButton(
                        onClick = onContact,
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Email",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MentorsSection(
    mentors: List<M3EMentorItem>,
    onEmail: (String) -> Unit,
    onPhone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SupervisorAccount,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Academic Leadership & Mentors",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
            }

            mentors.forEach { mentor ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mentor.role,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary
                        )
                        Text(
                            text = mentor.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        if (!mentor.department.isNullOrBlank()) {
                            Text(
                                text = mentor.department,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!mentor.phone.isNullOrBlank()) {
                            IconButton(
                                onClick = { onPhone(mentor.phone) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceContainerHigh)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Phone,
                                    contentDescription = "Call",
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (!mentor.email.isNullOrBlank()) {
                            IconButton(
                                onClick = { onEmail(mentor.email) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Email,
                                    contentDescription = "Email",
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalDetailsCard(
    state: M3EProfileState,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Contact & Profile Details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            ProfileDataRow(label = "Student Email", value = state.email)
            ProfileDataRow(label = "Phone Number", value = state.phone)
            ProfileDataRow(label = "School", value = state.school)
            ProfileDataRow(label = "Campus Location", value = state.campus)
        }
    }
}

@Composable
private fun PersonalRecordsSection(
    records: List<M3EFieldItem>,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Personal Records",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            records.forEach { record ->
                ProfileDataRow(label = record.label, value = record.value)
            }
        }
    }
}

@Composable
private fun AddressRecordsSection(
    addresses: List<M3EAddressItem>,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Address Records",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            addresses.forEach { addr ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = addr.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                    addr.lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialsCard(
    username: String,
    password: String,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "VTOP Credentials Vault",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Username",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.onSurface
                    )
                }

                IconButton(
                    onClick = { onCopy(username, "Username") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy Username",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (password.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Password",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = if (showPassword) password else "••••••••••••",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onToggleVisibility,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle Password Visibility",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { onCopy(password, "Password") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Password",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDataRow(
    label: String,
    value: String
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Text(
            text = orDash(value),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
    }
}

/** Missing profile data renders as an em dash, never as a plausible-looking placeholder. */
private fun orDash(value: String): String = value.ifBlank { "—" }

/** A negative value is the bridge's "no data" sentinel; real values always show 2 decimals. */
private fun profileMetricValue(value: Double, suffix: String = ""): String =
    if (value < 0.0) "—" else value.toFixed(2, suffix)

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3EProfileScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EProfileScreen(
            state = createMockProfileState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3EProfileScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EProfileScreen(
            state = createMockProfileState(),
            onAction = {}
        )
    }
}

private fun createMockProfileState() = M3EProfileState(
    studentName = "Aditya Sharma",
    registrationNumber = "22BCE1045",
    applicationNumber = "1202204567",
    programme = "B.Tech Computer Science & Engineering",
    school = "School of Computer Science & Engineering (SCOPE)",
    campus = "Vellore Campus",
    dob = "14 Aug 2004",
    bloodGroup = "O +ve",
    email = "aditya.sharma2022@vitstudent.ac.in",
    phone = "+91 98401 22334",
    proctorName = "Dr. Anita R",
    proctorEmail = "anita.r@vit.ac.in",
    proctorCabin = "SJT 413-A",
    hostelBlock = "Q Block",
    hostelRoom = "514",
    cgpa = 9.24,
    attendancePercentage = 91.5,
    isIdCardFlipped = false
)
