# AmazeCC — Kotlin Android App

> The native Android port of [AmazeCC](../AmazeCC), the unified student operating system for VIT University.
> Built with **Kotlin Multiplatform + Jetpack Compose**, communicating with `api.amazecc.com`.

---

## Overview

AmazeCC Kotlin is a native Android app that brings the full AmazeCC web experience to your phone. It connects to the same backend API (`api.amazecc.com`), syncing your academic data directly from VTOP — attendance, grades, timetable, hostel details, payments, library, transport, and more — all in one place with a beautiful pitch-black Midnight theme.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose (Compose Multiplatform) |
| Language | Kotlin |
| Networking | Ktor (HTTP client) |
| Serialization | Kotlinx Serialization |
| State Management | Kotlin StateFlow / Coroutines |
| Architecture | KMP (Kotlin Multiplatform) — `shared` module + `androidApp` |
| API | `https://api.amazecc.com` |

---

## Getting Started

### Prerequisites
- **Android Studio** (Hedgehog or newer)
- **JDK 17+**
- **Android SDK 33+**
- A valid `local.properties` file in the project root (auto-created by Android Studio, or add manually):
  ```
  sdk.dir=C:\\Users\\<YOUR_USERNAME>\\AppData\\Local\\Android\\Sdk
  ```

### Run the App

**Option 1 — Android Studio:**
1. Open the `Amazecc-Kotlin` folder in Android Studio.
2. Wait for Gradle sync to complete.
3. Select the `androidApp` run configuration.
4. Press ▶ Run on an emulator or connected device.

**Option 2 — Terminal:**
```bash
# Install on a connected device / running emulator
./gradlew androidApp:installDebug

# Or just compile to verify
./gradlew androidApp:compileDebugKotlin
```

---

## Feature Phases

### ✅ Phase 1 — Core Foundation & UI
> **Status: Complete**

| Feature | Status |
|---|---|
| Design System (Colors, Typography, Shapes) matching web app | ✅ Done |
| Light / Dark / **Midnight** (OLED pitch-black) themes | ✅ Done |
| 4 Accent palettes: Ocean, Forest, Lavender, Sunset | ✅ Done |
| Ktor API client wired to `api.amazecc.com` | ✅ Done |
| Session / Cookie management (`SessionManager`) | ✅ Done |
| Login screen with VTOP credentials (captcha auto-solved) | ✅ Done |
| Bottom Navigation Bar (floating pill, pitch-black) | ✅ Done |
| Global Scaffold + animated screen transitions | ✅ Done |
| App icon from web app favicon | ✅ Done |

---

### ✅ Phase 2 — Core Academic Features
> **Status: Complete**

| Feature | Status |
|---|---|
| **Attendance Hub** — course list with % badges and progress bars | ✅ Done |
| **Marks & GPA Hub** — internal marks + grade history tabs | ✅ Done |
| **Timetable / Schedule Hub** — registered course cards with slot/venue | ✅ Done |
| Dashboard quick-stats cards (Attendance %, CGPA, Library, Wallet) | ✅ Done |
| Semester selector dropdown on Dashboard | ✅ Done |
| Sync status + error banners | ✅ Done |

---

### 🔲 Phase 3 — Campus Services
> **Status: Planned**

| Feature | Status |
|---|---|
| **Payments** — billing dues, wallet balance, receipt history | 🔲 Planned |
| **Library** — issued books, fines, catalog search | 🔲 Planned |
| **Transport** — bus routes, timings, day-boarder pass status | 🔲 Planned |
| **LMS** — assignments list, due dates, submission status | 🔲 Planned |
| **Exam Schedule** — hall ticket seat/venue per course | 🔲 Planned |

> Note: The UI shells for these screens are already stubbed in `MainScreens.kt`. Phase 3 is primarily about data binding validation, edge-case handling, and UI polish.

---

### 🔲 Phase 4 — Hostel & Residential
> **Status: Planned**

| Feature | Status |
|---|---|
| **Hostel Portal** — block/room/mess info | 🔲 Planned |
| **Leave History** — outings & leave approvals | 🔲 Planned |
| **Late Hour Requests** — submit extension requests | 🔲 Planned |
| **Hosteller vs. Day Scholar** conditional navigation (mirrors web nav logic) | 🔲 Planned |

---

### 🔲 Phase 5 — Social & Advanced Features
> **Status: Planned**

| Feature | Status |
|---|---|
| Academic Calendar | 🔲 Planned |
| Question Bank | 🔲 Planned |
| Events & Clubs | 🔲 Planned |
| Friends / Student Directory | 🔲 Planned |
| FFCS (Course Registration) viewer | 🔲 Planned |
| Notifications & Background Sync | 🔲 Planned |

---

## Project Structure

```
Amazecc-Kotlin/
├── androidApp/                   # Android entry point
│   └── src/main/
│       ├── kotlin/               # MainActivity.kt
│       ├── res/mipmap-*/         # App icons (from AmazeCC favicon)
│       └── AndroidManifest.xml
│
└── shared/                       # KMP shared module (all UI + logic)
    └── src/commonMain/kotlin/com/amazecc/app/shared/
        ├── api/                  # AmazeClient.kt (Ktor, api.amazecc.com)
        ├── model/                # Kotlin data classes (Models.kt)
        ├── repository/           # SessionManager.kt
        ├── state/                # AppState.kt (global StateFlow store)
        ├── theme/                # AmazeTheme.kt, Color.kt, Typography.kt
        └── ui/
            ├── components/       # AmazeCard, AmazeBadge, BottomNavigationBar, etc.
            └── screens/          # LoginScreen, DashboardScreen, MainScreens.kt
```

---

## Theme System

The app uses the same color language as the AmazeCC web app:

| Theme | Background | Surface | Description |
|---|---|---|---|
| Light | `#F9FAFB` | `#FFFFFF` | Clean white mode |
| Dark | `#111827` | `#1F2937` | Slate dark mode |
| **Midnight** | `#000000` | `#0A0A0C` | **OLED pitch-black** |

Accent colors (user-selectable): **Ocean** `#0EA5E9` · **Forest** `#10B981` · **Lavender** `#8B5CF6` · **Sunset** `#F97316`

---

## API

All data is fetched via the shared `AmazeClient` using Ktor from:

```
https://api.amazecc.com
```

The API is the same backend used by the AmazeCC web app. Authentication uses VTOP session cookies managed by `SessionManager`.

---

## Related Projects

- [AmazeCC (Web)](../AmazeCC) — The original Next.js web app
- [AmazeCC API](../AmazeCC-API) — The Node.js backend API proxy
