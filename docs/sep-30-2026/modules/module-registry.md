# Module Registry

Every module that needs porting, with the exact upstream call and the `AmazeClient` entry point.

Shared request shape for the `verifyMenu` family — every Group A/B/C endpoint except where noted:

```
POST /vtop/<path>
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
Referer: https://vtopcc.vit.ac.in/vtop/open/page

verifyMenu=true&authorizedID=<id>&_csrf=<csrf>&nocache=<epochMillis>
```

`nocache` is a 13-digit epoch millisecond value. See `../sep-29-2026/vtop-porting-notes.md` for the
login flow that produces `_csrf` and `authorizedID`.

## Group A — generic page parse

| Module | Endpoint | Extra params | Client method | DTO |
|---|---|---|---|---|
| APAAR | `/vtop/apaarid/upload` | — | `getApaarId` | `ApaarIdRes` |
| EPT_SCHEDULE | `/vtop/compre/eptScheduleShow` | — | `getEptSchedule` | `EptScheduleRes` |
| REGISTRATION_SCHEDULE | `/vtop/examinations/hostelDetails` | — | `getRegistrationSchedule` | `RegistrationScheduleRes` |
| UNIVERSITY_DAY | `/vtop/event/uday/certificates` | — | `getUniversityDay` | `UniversityDayRes` |
| DAYBOARDER | `/vtop/admissions/dayboarderForMenu` | — | `getDayboarderInfo` | `DayboarderRes` |

## Group B — key/value label scan

| Module | Endpoint(s) | Client method | DTO |
|---|---|---|---|
| STUDENT_PROFILE | `/vtop/studentsRecord/StudentProfileAllView` | `getStudentProfile` | `StudentProfileRes` |
| BANK_INFO | `/vtop/studentBankInformation/BankInfoStudent` | `getBankInfo` | `BankInfoRes` |
| PROFILE_IMAGES | `/vtop/proctor/viewProctorDetails`<br>`/vtop/hrms/viewHodDeanDetails`<br>`/vtop/proctor/viewStudentCredentials` | `getProfileImages` | `ProfileImagesRes` |

## Group C — table extractors

| Module | Endpoint | Extra params | Client method | DTO |
|---|---|---|---|---|
| EXAM_SCHEDULE | `/vtop/examinations/doSearchExamScheduleForStudent` | **`semesterSubId`** | `getExamSchedule` | `ExamScheduleRes` |
| CREDENTIALS | `/vtop/proctor/viewStudentCredentials` | — | `getCredentials` | `CredentialsRes` |
| CIRCULARS | `/vtop/admissions/costCentreCircularsViewPageController` | — | `getCirculars` | `CircularsRes` |
| QCM_VIEW | `/vtop/academics/common/QCMStudentLogin`<br>→ `/vtop/getStudentLoginForQcm` | **`semSubId`**, `paramReturnId`, own `pageCsrf` | `getQcmView` | `QcmViewRes` |
| CALENDAR + CALENDARS_LIST | `/vtop/processViewCalendar` × 5–7 months | **`semSubId`**, `calDate`, `classGroupId` | `getCalendar`, `getCalendars` | `CalendarRes`, `CalendarsListRes` |

## Group D — curriculum

| Module | Endpoint | Extra params | Client method | DTO |
|---|---|---|---|---|
| CURRICULUM | `/vtop/academics/common/Curriculum`<br>→ `/vtop/academics/common/curriculumCategoryView` | `categoryId`, `x`=**UTC string** | `getCurriculum` | `CurriculumRes` |
| CURRICULUM syllabus | `POST /vtop/courseSyllabusDownload1` | `courseCode`, `_csrf` **twice** | `getSyllabusPdf` | bytes |
| CURRICULUM download | `POST /vtop/academics/curriculDown` | `regNo=NONE`, `_csrf` **twice** | — | bytes |

## Group E — other hosts

| Module | Host | Auth | Client method | DTO |
|---|---|---|---|---|
| LMS | `lms.vit.ac.in` | own Moodle login → `logintoken` + `sesskey` | `getLMSAssignments` | `LMSRes` |
| EVENTS | `eventhubcc.vit.ac.in` | **public**, no auth | `getEvents` | `EventHubRes` |
| EVENTS_PROFILE | `eventhubcc.vit.ac.in` | `JSESSIONID` + `cookiesession1` | `getEventsProfile` | `EventHubRegisteredEventsRes` |
| MOODLE | `lms.vit.ac.in` | user credentials | — | — |

## Response casing notes

- EXAM_SCHEDULE emits **uppercase `"Schedule"`** only. The DTO carries
  `@SerialName("Schedule") rawScheduleUpper` and a lowercase `rawScheduleLower` with a computed
  `schedule` accessor, so both are already tolerated.
- Group A returns camelCased `keyValuePairs` keys; the DTOs map by those names.
- LMS and EVENTS return **bare JSON arrays**, not objects.
- EVENTS_PROFILE returns `{ "events": [...] }`.
