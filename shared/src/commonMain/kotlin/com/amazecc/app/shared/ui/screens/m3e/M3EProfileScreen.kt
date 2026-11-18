package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Digital Student ID & Profile Screen.
 * 100% Stateless: accepts [M3EProfileState] and emits [M3EProfileAction].
 * Features double-sided Digital ID card with library barcode, proctor contacts, and academic stats.
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
                            .background(colors.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.primary,
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
                    value = "${state.cgpa}",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "ATTENDANCE",
                    value = "${state.attendancePercentage}%",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "CAMPUS",
                    value = state.campus,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Proctor Card
        item {
            ProctorContactCard(
                state = state,
                onContact = { onAction(M3EProfileAction.ContactProctor) }
            )
        }

        // 5. Personal Information Card
        item {
            PersonalDetailsCard(state = state)
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

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onFlip)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // University Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VELLORE INSTITUTE OF TECHNOLOGY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        ),
                        color = colors.primary
                    )
                    Text(
                        text = "STUDENT IDENTITY CARD",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.onSurfaceVariant
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
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.studentName,
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
                                text = state.registrationNumber,
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
                            text = "${state.programme} • ${state.school}",
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
                        .background(colors.surfaceContainerHighest)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Simulated barcode stripes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(32) { index ->
                            Box(
                                modifier = Modifier
                                    .width(if (index % 3 == 0) 3.dp else 1.5.dp)
                                    .height(28.dp)
                                    .background(colors.onSurface)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "* ${state.registrationNumber} *",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                // Back Side: Blood Group, Emergency, Hostel Room, Advisor
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Card Reverse Details",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )

                    ProfileDataRow(label = "Blood Group", value = state.bloodGroup)
                    ProfileDataRow(label = "Date of Birth", value = state.dob)
                    ProfileDataRow(label = "Application Number", value = state.applicationNumber)
                    ProfileDataRow(label = "Hostel & Room", value = "${state.hostelBlock ?: "Dayscholar"} • ${state.hostelRoom ?: "-"}")
                    ProfileDataRow(label = "Faculty Proctor", value = state.proctorName)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (state.isIdCardFlipped) "Tap card to flip to front" else "Tap card to view details",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
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
        cornerRadius = 18.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
private fun ProctorContactCard(
    state: M3EProfileState,
    onContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                Column {
                    Text(
                        text = state.proctorName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = state.proctorEmail,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
    }
}

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
