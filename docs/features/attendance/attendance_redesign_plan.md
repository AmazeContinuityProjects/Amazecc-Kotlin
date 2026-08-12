# Attendance Module Redesign & Predictor Enhancements

## Overview
This document specifies the architectural updates and UI/UX redesign for the **Attendance** system in Amazecc-Kotlin.

The changes address three key goals:
1. **Home Screen Customization & Header Optimization**: Option to integrate the Overall Attendance percentage card directly into the **Quick Stats** cards row, allowing users to remove the large Attendance widget to free up space. Additionally, top margin on the home screen is tightened to only account for the system status/notification bar.
2. **Predictor UI Revamp**: Redesigning the Attendance Predictor view in `AttendanceScreen.kt` using the cohesive, modern design system from **Academics → Course Hub → Course Detail Page** (hero gradient cards, glassmorphic containers, `SettingsGroupCard` menu aesthetics, and spring-animated chip selectors).
3. **Advanced Predictor Calculations & Bus Pass Target**:
   - Support for **Bus Pass Target** (85% attendance required for bus subscribers) alongside the standard university target (75%).
   - Calculation of **Max Classes to Bunk** per subject to remain at or above the desired target.
   - Calculation of **Consecutive Classes to Attend Right Now** to get into the **Safe Zone**.
   - **Exam Safe Zone Indicator** showing attendance status and safe zone requirements for upcoming exams (CAT-I, CAT-II, FAT, LID).

---

## 1. Home Dashboard & Quick Stats Integration

### 1.1 Quick Stats Attendance Card
- Add a new setting `KEY_SHOW_ATTENDANCE_IN_STATS` in `SettingsManager` and `AppState`.
- When enabled, `MetricCardsWidget()` appends an **Attendance Metric Card**:
  - **Value**: Overall attendance percentage (e.g. `86%`).
  - **Icon**: `Icons.Rounded.CheckCircle`.
  - **Dynamic Theme Tint**:
    - $\ge 75\%$ (or $\ge 85\%$ if bus subscriber): Success theme color.
    - $50\% - 74\%$: Warning theme color.
    - $< 50\%$: Danger theme color.
  - **Interaction**: Tapping navigates straight to `Screen.ATTENDANCE` / Predictor.
- Users can remove or hide the large `ATTENDANCE_BUNK` widget via the existing Dashboard Widget Management menu (`AppState.removeWidget(DashboardWidget.ATTENDANCE_BUNK)`).

### 1.2 Home Screen Top Header Space Optimization
- Remove superfluous top spacing in `WidgetDashboard` (`DashboardWidgets.kt`).
- Ensure padding strictly aligns with system status bar insets using `WindowInsets.statusBars` / `Modifier.statusBarsPadding()`, preventing double-padding or wasted header space.

---

## 2. Attendance Predictor UI/UX Revamp

The Predictor will adopt the design language of `CourseDetailScreen.kt`:
1. **Hero Gradient Header (`AttendancePredictorHeroCard`)**:
   - Modern linear gradient backdrop (`colors.accent` to `colors.accent.copy(alpha = 0.6f)`).
   - Display overall predicted percentage with circular progress indicator.
   - **Target Badge**: Clear display of current target (75% Standard vs 85% Bus Subscriber) with quick switch toggle.
   - **Health Status Badge**: "Safe Zone", "Watch Zone", or "Critical".
2. **Cutoff Target Chip Selector**:
   - Sleek spring-animated pill chips for CAT-I, CAT-II, FAT, and LID exam cutoffs.
   - Key stats row showing Calendar Days, Remaining Working Days, and Total Months.
3. **Multi-Day Batch Bunk Simulator**:
   - Glassmorphic card allowing users to simulate skipping 1–7 upcoming working days across all courses simultaneously.
4. **Per-Course Predictor Breakdown Cards (`ExpandedCoursePredictorCard`)**:
   - Subject code, title, slot, and faculty.
   - Side-by-side stats: Current Attended, Max Bunk Allowance, Safe Zone Requirement.
   - Date-by-date upcoming class chips to toggle individual skips.

---

## 3. Mathematical Models & Safe Zone Logic

### 3.1 Target Attendance Threshold $P_{target}$
$$\begin{cases} 
0.85 & \text{if Bus Subscriber toggle is active} \\ 
0.75 & \text{if Standard Student} 
\end{cases}$$

### 3.2 Max Classes to Bunk ($B_{max}$)
For a subject with current attended classes $A$, current total classes $T$, and remaining future classes up to the selected cutoff $F$:
$$B_{max} = \max\left(0, \left\lfloor A + F - P_{target} \times (T + F) \right\rfloor\right)$$
- If $B_{max} > 0$: The student can skip up to $B_{max}$ classes without falling below $P_{target}$.
- If $B_{max} = 0$: The student cannot afford to skip any remaining classes.

### 3.3 Safe Zone Class Requirement ($C_{need}$)
If current percentage $\frac{A}{T} < P_{target}$, the student is in the **Risk Zone**. The consecutive next classes $C_{need}$ they must attend *right now* to reach $P_{target}$ is:
$$C_{need} = \max\left(0, \left\lceil \frac{P_{target} \times T - A}{1 - P_{target}} \right\rceil\right)$$

### 3.4 Exam Safe Zone Status
For the active cutoff date (CAT-1, CAT-2, FAT, LID), the predictor calculates expected percentage $P_{exam}$:
- If $P_{exam} \ge P_{target}$: **"Safe for Exam"** (green badge).
- If $P_{exam} < P_{target}$: **"⚠️ Below Safe Zone for Exam"** (danger badge), showing $C_{need}$ required before exam day.

---

## 4. Implementation Schedule

1. **Phase 1: Settings & State**: Add `KEY_SHOW_ATTENDANCE_IN_STATS` and `KEY_BUS_SUBSCRIBER` to `SettingsManager` and `AppState`.
2. **Phase 2: Dashboard Updates**: Update `MetricCardsWidget` to render the Attendance Metric Card when enabled; clean up top status bar spacing in `WidgetDashboard`.
3. **Phase 3: Attendance Predictor Revamp**: Refactor `OverallPredictorScreen` in `AttendanceScreen.kt` to use the Course Detail visual style, bus subscriber target logic, bunk calculator, and safe zone indicators.
4. **Phase 4: Verification**: Verify build integrity, widget state persistence, and responsive layout across light/dark themes.
