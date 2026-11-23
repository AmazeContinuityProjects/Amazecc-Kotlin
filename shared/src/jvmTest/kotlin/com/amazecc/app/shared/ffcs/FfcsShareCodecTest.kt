package com.amazecc.app.shared.ffcs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-179 — byte-compatibility gates for all four FFCS share formats.
 *
 * Golden payloads come straight from the ffcs-planner skill asset, which was
 * generated with the real npm `lz-string` v1.4.5. A change to the bit writer,
 * dictionary growth, JSON key order, LEB128 loop or base64url alphabet fails
 * the equality tests below rather than silently emitting a different link.
 */
class FfcsShareCodecTest {

    private val golden: JsonObject by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/ffcs/golden-share.json")) {
            "Missing test resource /ffcs/golden-share.json"
        }
        Json.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonObject
    }

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content

    private val encoded: JsonObject get() = golden.getValue("encoded").jsonObject

    private val plan: FfcsShareCodec.FfcsShareTimetable by lazy {
        val raw = golden.getValue("plan").jsonObject
        FfcsShareCodec.FfcsShareTimetable(
            name = raw.str("name"),
            courses = raw.getValue("courses").jsonArray.map { element ->
                val course = element.jsonObject
                FfcsShareCodec.FfcsShareCourse(
                    id = course.str("id"),
                    code = course.str("code"),
                    title = course.str("title"),
                    slots = course.getValue("slots").jsonArray.map { it.jsonPrimitive.content },
                    faculty = course.str("faculty"),
                    venue = course.str("venue"),
                    credits = course.str("credits"),
                    type = course.str("type"),
                    color = course.str("color"),
                    classId = course.str("classId"),
                    classIds = course.getValue("classIds").jsonArray.map { it.jsonPrimitive.content },
                )
            },
        )
    }

    private fun compress(json: String): String = FfcsLzString.compressToEncodedUriComponent(json)

    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    // ── Byte-compatibility gates ───────────────────────────────────────────

    @Test
    fun `golden share strings re-encode byte for byte`() {
        assertEquals(encoded.str("full"), FfcsShareCodec.encodeTimetableShare(plan, "chennai"))
        assertEquals(
            encoded.str("compact"),
            FfcsShareCodec.encodeCompactTimetableShare(plan, FfcsSemesterId.WINTER_FINAL),
        )
        assertEquals(
            encoded.str("shape"),
            FfcsShareCodec.encodeTimetableShapeShare(plan, FfcsSemesterId.WINTER_FINAL),
        )
        assertEquals(
            encoded.str("numeric"),
            FfcsShareCodec.encodeNumericShapeShare(plan, FfcsSemesterId.WINTER_FINAL),
        )
        assertEquals("2.bOYI", encoded.str("numeric"))
    }

    @Test
    fun `golden payloads decode to the documented objects`() {
        val full = checkNotNull(FfcsShareCodec.decodeTimetableShare(encoded.str("full")))
        assertEquals("My Week", full.timetable.name)
        assertEquals("chennai", full.campus)
        assertEquals(plan.courses, full.timetable.courses)
        assertTrue(full.timetable.id.startsWith("share-"))

        val compact = checkNotNull(FfcsShareCodec.decodeCompactTimetableShare(encoded.str("compact")))
        assertEquals(1, compact.v)
        assertEquals("winter-2026-2027", compact.s)
        assertEquals("CH202627050", compact.p)
        assertEquals(listOf("1234", "0108"), compact.i)
        assertEquals("My Week", compact.n)

        val shape = checkNotNull(FfcsShareCodec.decodeTimetableShapeShare(encoded.str("shape")))
        assertEquals(FfcsSemesterId.WINTER_FINAL, shape.semesterId)
        assertEquals("CH202627050", shape.prefix)
        // Shape suffixes stay in wire order; only the numeric codec sorts.
        assertEquals(listOf("CH2026270501234", "CH2026270500108"), shape.classIds)

        val numeric = checkNotNull(FfcsShareCodec.decodeNumericShapeShare(encoded.str("numeric")))
        assertEquals(FfcsSemesterId.WINTER_FINAL, numeric.semesterId)
        assertEquals(listOf("CH2026270500108", "CH2026270501234"), numeric.classIds)
    }

    @Test
    fun `full round trip preserves courses but assigns a fresh id`() {
        val payload = FfcsShareCodec.encodeTimetableShare(plan)
        val first = checkNotNull(FfcsShareCodec.decodeTimetableShare(payload))
        val second = checkNotNull(FfcsShareCodec.decodeTimetableShare(payload))
        assertEquals(plan.name, first.timetable.name)
        assertEquals(plan.courses, first.timetable.courses)
        assertNotEquals(first.timetable.id, second.timetable.id)
        assertTrue(payload.matches(Regex("[A-Za-z0-9+\$/=-]+")))
    }

    @Test
    fun `lz string handles quotes control characters and unicode`() {
        val text = "{\"n\":\"a\\\"b\\nc\",\"emoji\":\"😀\",\"name\":\"\u0158\u00f6\u4f60\"}"
        assertEquals(text, FfcsLzString.decompressFromEncodedUriComponent(FfcsLzString.compressToEncodedUriComponent(text)))
    }

    // ── Full refusals ───────────────────────────────────────────────────────

    @Test
    fun `full decode refuses empty garbage truncated version bumped and empty courses`() {
        assertNull(FfcsShareCodec.decodeTimetableShare(""))
        assertNull(FfcsShareCodec.decodeTimetableShare("not-a-share-link"))
        val goldenFull = encoded.str("full")
        assertNull(FfcsShareCodec.decodeTimetableShare(goldenFull.take(goldenFull.length / 2)))

        val future = compress(
            """{"v":2,"c":"chennai","n":"x","k":[{"id":"a","code":"CSE1001","slots":["A1"],"faculty":"","venue":"","credits":"","type":"","color":""}]}""",
        )
        assertNull(FfcsShareCodec.decodeTimetableShare(future))

        val empty = compress("""{"v":1,"c":"chennai","n":"x","k":[]}""")
        assertNull(FfcsShareCodec.decodeTimetableShare(empty))

        val notCourses = compress("""{"v":1,"c":"chennai","n":"x","k":[{"nope":true}]}""")
        assertNull(FfcsShareCodec.decodeTimetableShare(notCourses))

        val badSlots = compress(
            """{"v":1,"c":"chennai","n":"x","k":[{"code":"CSE1001","slots":["A1",7]}]}""",
        )
        assertNull(FfcsShareCodec.decodeTimetableShare(badSlots))

        val stringVersion = compress("""{"v":"1","c":"chennai","n":"x","k":[{"code":"CSE1001","slots":[]}]}""")
        assertNull(FfcsShareCodec.decodeTimetableShare(stringVersion))
    }

    // ── Compact validation ──────────────────────────────────────────────────

    @Test
    fun `compact decode validates every field exactly`() {
        val previous = compress("""{"v":1,"s":"previous","p":"CH202627050","i":["1234"],"n":"x"}""")
        assertEquals("fall-2026-2027", checkNotNull(FfcsShareCodec.decodeCompactTimetableShare(previous)).s)

        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":2,"s":"winter-2026-2027","p":"CH202627050","i":["1234"],"n":"x"}""")))
        // The mock semester is deliberately not in the compact wire's accepted set.
        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":1,"s":"winter-2026-2027-mock","p":"CH202627050","i":["1234"],"n":"x"}""")))
        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":1,"s":"winter-2026-2027","p":"CH","i":["1234"],"n":"x"}""")))
        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":1,"s":"winter-2026-2027","p":"CH!202627050","i":["1234"],"n":"x"}""")))
        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":1,"s":"winter-2026-2027","p":"CH202627050","i":[],"n":"x"}""")))
        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":1,"s":"winter-2026-2027","p":"CH202627050","i":["1234","1234"],"n":"x"}""")))
        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":1,"s":"winter-2026-2027","p":"CH202627050","i":["1 2"],"n":"x"}""")))
        assertNull(FfcsShareCodec.decodeCompactTimetableShare(compress("""{"v":1,"s":"winter-2026-2027","p":"CH202627050","i":["1234"],"n":"${"x".repeat(121)}"}""")))
    }

    @Test
    fun `compact encode refuses missing duplicate or too-short ids`() {
        val noIds = FfcsShareCodec.FfcsShareTimetable(
            name = "x",
            courses = listOf(plan.courses[0].copy(classId = null, classIds = null)),
        )
        assertNull(FfcsShareCodec.encodeCompactTimetableShare(noIds, FfcsSemesterId.WINTER_FINAL))

        val duplicate = FfcsShareCodec.FfcsShareTimetable(
            name = "x",
            courses = listOf(plan.courses[0], plan.courses[0]),
        )
        assertNull(FfcsShareCodec.encodeCompactTimetableShare(duplicate, FfcsSemesterId.WINTER_FINAL))

        val short = FfcsShareCodec.FfcsShareTimetable(
            name = "x",
            courses = listOf(plan.courses[0].copy(classId = "AB12", classIds = null)),
        )
        assertNull(FfcsShareCodec.encodeCompactTimetableShare(short, FfcsSemesterId.WINTER_FINAL))
    }

    @Test
    fun `shape codec rejects unknown namespaces and bad suffixes`() {
        assertNull(FfcsShareCodec.decodeTimetableShapeShare(compress("""{"p":"XXXX","i":["1234"]}""")))
        assertNull(FfcsShareCodec.decodeTimetableShapeShare(compress("""{"p":"CH202627050","i":["1234","1234"]}""")))
        assertNull(FfcsShareCodec.decodeTimetableShapeShare(compress("""{"p":"CH202627050","i":["12!4"]}""")))

        val wrongSemester = FfcsShareCodec.FfcsShareTimetable(
            name = "x",
            courses = listOf(plan.courses[0].copy(classId = "CH2026270101234", classIds = null)),
        )
        assertNull(FfcsShareCodec.encodeTimetableShapeShare(wrongSemester, FfcsSemesterId.WINTER_FINAL))
    }

    // ── Numeric edge cases ──────────────────────────────────────────────────

    @Test
    fun `numeric codec rejects malformed payloads`() {
        assertNull(FfcsShareCodec.decodeNumericShapeShare(""))
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2."))
        assertNull(FfcsShareCodec.decodeNumericShapeShare(".bOYI"))
        assertNull(FfcsShareCodec.decodeNumericShapeShare("3.bOYI"))
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.***"))
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.A")) // 4n+1 base64 length

        // Non-canonical two-byte varint for value 1.
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.gQA"))
        // Zero delta after the first id, a single value > 9999, cumulative > 9999,
        // and a three-byte varint are all refused.
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.bAA"))
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.kE4")) // single delta 10000
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.j04B")) // 9999 then +1 overflows
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.gIAB"))

        val hundredAndOne = ByteArray(101) { 1 }
        assertNull(FfcsShareCodec.decodeNumericShapeShare("2.${b64url(hundredAndOne)}"))
    }

    @Test
    fun `numeric codec accepts canonical boundary payloads`() {
        assertEquals(
            listOf("CH2026270500000"),
            checkNotNull(FfcsShareCodec.decodeNumericShapeShare("2.AA")).classIds,
        )
        assertEquals(
            listOf("CH2026270500002"),
            checkNotNull(FfcsShareCodec.decodeNumericShapeShare("2.Ag")).classIds,
        )
        assertEquals(
            listOf("CH2026270509999"),
            checkNotNull(FfcsShareCodec.decodeNumericShapeShare("2.j04")).classIds,
        )

        val mock = checkNotNull(FfcsShareCodec.decodeNumericShapeShare("1.Ag"))
        assertEquals(FfcsSemesterId.WINTER_MOCK, mock.semesterId)
        assertEquals(listOf("CH2026270500002"), mock.classIds)

        val fall = checkNotNull(FfcsShareCodec.decodeNumericShapeShare("${FfcsReports.SEMESTER_REPORTS.first { it.id == FfcsSemesterId.FALL }.classIdPrefixCode}.Ag"))
        assertEquals(FfcsSemesterId.FALL, fall.semesterId)
        assertEquals(listOf("CH2026270100002"), fall.classIds)

        assertEquals(
            "1.Ag",
            FfcsShareCodec.encodeNumericShapeShare(listOf("CH2026270500002"), "1", "CH202627050"),
        )
    }

    // ── URLs / receiver ─────────────────────────────────────────────────────

    @Test
    fun `share urls honour base path and percent encoding`() {
        assertEquals(
            "https://example.com/AmazeCC/share/ffcs/?d=abc",
            FfcsShareCodec.buildShareUrl("abc", "https://example.com/", basePath = "/AmazeCC"),
        )
        assertEquals(
            "https://example.com/share/ffcs/?d=abc",
            FfcsShareCodec.buildShareUrl("abc", "https://example.com"),
        )
        assertEquals(
            "https://example.com/share/ffcs/?x=a%2Bb",
            FfcsShareCodec.buildCompactShareUrl("a+b", "https://example.com"),
        )
        assertEquals(
            "https://example.com/share/ffcs/?q=abc",
            FfcsShareCodec.buildTimetableShapeShareUrl("abc", "https://example.com"),
        )
        assertEquals(
            "https://example.com/share/ffcs/?r=2.bOYI",
            FfcsShareCodec.buildNumericTimetableShapeShareUrl("2.bOYI", "https://example.com"),
        )
    }

    @Test
    fun `oversize boundary is strictly greater than 8000`() {
        assertEquals(8000, FfcsShareCodec.FFCS_SHARE_WARN_LENGTH)
        assertFalse(FfcsShareCodec.isOversizedShareLink("x".repeat(8000)))
        assertTrue(FfcsShareCodec.isOversizedShareLink("x".repeat(8001)))
    }

    @Test
    fun `receiver prefers q over r over x over d and detects bare codes in strictness order`() {
        val parsed = checkNotNull(
            FfcsShareCodec.parseShareInput("https://example.com/share/ffcs/?d=AAA&q=BBB&r=2.bOYI&x=CCC"),
        )
        assertEquals(FfcsShareCodec.FfcsShareInputFormat.SHAPE, parsed.format)
        assertEquals("BBB", parsed.encoded)

        val numeric = checkNotNull(FfcsShareCodec.parseShareInput("?r=2.bOYI"))
        assertEquals(FfcsShareCodec.FfcsShareInputFormat.NUMERIC_SHAPE, numeric.format)
        assertEquals("2.bOYI", numeric.encoded)

        assertEquals(FfcsShareCodec.FfcsShareInputFormat.FULL, FfcsShareCodec.parseShareInput(encoded.str("full"))?.format)
        assertEquals(FfcsShareCodec.FfcsShareInputFormat.COMPACT, FfcsShareCodec.parseShareInput(encoded.str("compact"))?.format)
        assertEquals(FfcsShareCodec.FfcsShareInputFormat.SHAPE, FfcsShareCodec.parseShareInput(encoded.str("shape"))?.format)
        assertEquals(FfcsShareCodec.FfcsShareInputFormat.NUMERIC_SHAPE, FfcsShareCodec.parseShareInput(encoded.str("numeric"))?.format)

        // The golden compact payload contains '+', so its built URL exercises
        // percent-decoding of %2B back into the exact original payload.
        val builtUrl = FfcsShareCodec.buildCompactShareUrl(encoded.str("compact"), "https://example.com/")
        val percentEncoded = checkNotNull(FfcsShareCodec.parseShareInput(builtUrl))
        assertEquals(FfcsShareCodec.FfcsShareInputFormat.COMPACT, percentEncoded.format)
        assertEquals(encoded.str("compact"), percentEncoded.encoded)
        assertNull(FfcsShareCodec.parseShareInput("   "))
    }
}
