package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.vtop.captcha.CaptchaMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Covers the parts of the local VTOP layer that need no device: captcha maths, the credential
 * escaping used when building login scripts, and User-Agent rotation.
 *
 * The WebView transport and the HTML parsing are exercised by running the app, not here.
 */
class VtopLogicTest {

    // ── Captcha block geometry ──────────────────────────────────────────────
    // Must match fkvit/lib/features/authentication/ocr_custom/constants.dart. A mismatch here
    // silently degrades recognition accuracy rather than throwing.

    @Test
    fun blockCoordinatesMatchReference() {
        for (i in 0 until CaptchaMath.NUM_CHARACTERS) {
            val c = CaptchaMath.blockCoordinates(i)
            val x1 = (i + 1) * 25 + 2
            val y1 = 7 + 5 * (i % 2) + 1
            val x2 = (i + 2) * 25 + 1
            val y2 = 35 - 5 * ((i + 1) % 2)
            assertEquals(listOf(x1, y1, x2, y2), c.toList(), "block $i geometry drifted")
        }
    }

    @Test
    fun everyBlockIs528Pixels() {
        for (i in 0 until CaptchaMath.NUM_CHARACTERS) {
            val c = CaptchaMath.blockCoordinates(i)
            val size = (c[2] - c[0]) * (c[3] - c[1])
            assertEquals(CaptchaMath.INPUT_SIZE, size, "block $i is not the expected 24x22")
        }
    }

    @Test
    fun blocksStayInsideTheCanvas() {
        for (i in 0 until CaptchaMath.NUM_CHARACTERS) {
            val c = CaptchaMath.blockCoordinates(i)
            assertTrue(c[0] >= 0 && c[1] >= 0, "block $i starts out of bounds")
            assertTrue(c[2] <= CaptchaMath.IMAGE_WIDTH, "block $i exceeds image width")
            assertTrue(c[3] <= CaptchaMath.IMAGE_HEIGHT, "block $i exceeds image height")
        }
    }

    // ── Charset ─────────────────────────────────────────────────────────────

    @Test
    fun charsetHasNoAmbiguousGlyphs() {
        assertEquals(CaptchaMath.NUM_CLASSES, CaptchaMath.CHARSET.length)
        listOf("I", "O", "0", "1").forEach {
            assertTrue(!CaptchaMath.CHARSET.contains(it), "charset must not contain $it")
        }
    }

    // ── Saturation ──────────────────────────────────────────────────────────

    @Test
    fun saturationOfGreyIsZero() {
        val rgb = FloatArray(3) { 128f }
        assertEquals(0f, CaptchaMath.saturation(rgb)[0])
    }

    @Test
    fun saturationOfPureRedIsMaximum() {
        val rgb = floatArrayOf(255f, 0f, 0f)
        assertEquals(255f, CaptchaMath.saturation(rgb)[0])
    }

    // ── Binarisation ────────────────────────────────────────────────────────

    @Test
    fun binarizeSplitsAtBlockMean() {
        val block = floatArrayOf(0f, 0f, 0f, 1f)
        val out = CaptchaMath.binarize(block)
        assertEquals(4, out.size)
        // mean is 0.25, so only the 1.0 survives strictly-greater
        assertEquals(0f, out[0])
        assertEquals(1f, out[3])
    }

    @Test
    fun binarizeOfEmptyBlockIsEmpty() {
        assertEquals(0, CaptchaMath.binarize(FloatArray(0)).size)
    }

    // ── Forward pass ────────────────────────────────────────────────────────

    @Test
    fun forwardProducesAProbabilityDistribution() {
        val input = FloatArray(CaptchaMath.INPUT_SIZE) { if (it % 2 == 0) 1f else 0f }
        val weights = FloatArray(CaptchaMath.INPUT_SIZE * CaptchaMath.NUM_CLASSES) { 0.01f }
        val biases = FloatArray(CaptchaMath.NUM_CLASSES) { 0.1f }

        val probs = CaptchaMath.forward(input, weights, biases, CaptchaMath.NUM_CLASSES)
        assertEquals(CaptchaMath.NUM_CLASSES, probs.size)
        assertEquals(1f, probs.sum(), 0.001f)
        assertTrue(probs.all { it >= 0f && it <= 1f })
    }

    @Test
    fun forwardIsStableForLargeLogits() {
        // Guards the softmax max-subtraction; without it these overflow to NaN.
        val input = FloatArray(CaptchaMath.INPUT_SIZE) { 1f }
        val weights = FloatArray(CaptchaMath.INPUT_SIZE * CaptchaMath.NUM_CLASSES) { 500f }
        val biases = FloatArray(CaptchaMath.NUM_CLASSES) { 200f }

        val probs = CaptchaMath.forward(input, weights, biases, CaptchaMath.NUM_CLASSES)
        assertTrue(probs.all { it.isFinite() }, "softmax overflowed")
        assertEquals(1f, probs.sum(), 0.001f)
    }

    @Test
    fun argmaxFindsTheLargestProbability() {
        val (index, value) = CaptchaMath.argmax(floatArrayOf(0.1f, 0.7f, 0.2f))
        assertEquals(1, index)
        assertEquals(0.7f, value)
    }

    // ── Script escaping ─────────────────────────────────────────────────────
    // Credentials are interpolated into JavaScript. The reference implementation escapes only
    // single quotes, which breaks on backslashes and newlines.

    @Test
    fun jsStringEscapesQuotes() {
        assertEquals("\"a\\\"b\"", VtopScripts.jsString("a\"b"))
    }

    @Test
    fun jsStringEscapesBackslashes() {
        assertEquals("\"a\\\\b\"", VtopScripts.jsString("a\\b"))
    }

    @Test
    fun jsStringEscapesNewlines() {
        assertEquals("\"a\\nb\"", VtopScripts.jsString("a\nb"))
    }

    @Test
    fun jsStringEscapesControlCharacters() {
        assertEquals("\"a\\u0001b\"", VtopScripts.jsString("a\u0001b"))
    }

    @Test
    fun jsStringLeavesOrdinaryTextAlone() {
        assertEquals("\"STU123\"", VtopScripts.jsString("STU123"))
    }

    @Test
    fun aQuoteInAPasswordCannotBreakOutOfTheLiteral() {
        val hostile = "p')); alert(1); ('"
        val literal = VtopScripts.jsString(hostile)
        assertTrue(literal.startsWith("\"") && literal.endsWith("\""))

        // `jsString` emits a *double*-quoted literal, so the only quote that can terminate it is
        // `"`. That is the one that must be escaped as \".
        val withDoubleQuote = VtopScripts.jsString("pw\"); alert(1); //")
        assertEquals(
            "pw\\\"); alert(1); //",
            withDoubleQuote.drop(1).dropLast(1),
            "the double quote must be backslash-escaped so the literal cannot end early"
        )

        // A backslash is the other breakout vector: an unescaped one would escape the closing
        // quote and leave the literal unterminated.
        assertTrue(VtopScripts.jsString("pw\\").contains("\\\\"))

        // A single quote is inert inside a double-quoted literal and is deliberately NOT escaped -
        // escaping it would be harmless but the length arithmetic in the old assertion was wrong.
        assertTrue(literal.contains("alert(1)"))
        assertEquals(hostile.length + 2, literal.length)
    }

    // ── Login script shape ──────────────────────────────────────────────────

    @Test
    fun loginScriptSerializesTheWholeForm() {
        val script = VtopScripts.submitLogin("u", "p", "ABC123")
        // Serialising the form (not hand-built fields) is what carries _csrf
        assertTrue(script.contains("\$('#vtopLoginForm').serialize()") || script.contains("\$(form).serialize()"))
        assertTrue(script.contains("authorizedIDX"))
        assertTrue(script.contains("___INTERNAL___RESPONSE___"))
    }

    @Test
    fun captchaGoesIntoBothcaptchaStrAndGResponse() {
        val script = VtopScripts.submitLogin("u", "p", "ABC123")
        assertTrue(script.contains("captchaStr"))
        assertTrue(script.contains("gResponse"))
    }

    @Test
    fun shortCaptchaIsNormalisedButLongTokenIsPassedThrough() {
        val image = VtopScripts.submitLogin("u", "p", "ab-cd")
        assertTrue(image.contains("\"ABCD\""), "image captcha should be uppercased and stripped")

        val token = "A".repeat(150)
        val recaptcha = VtopScripts.submitLogin("u", "p", token)
        assertTrue(recaptcha.contains(token), "reCAPTCHA token should pass through verbatim")
    }

    // ── Prelogin script shape ───────────────────────────────────────────────

    @Test
    fun preloginAsksWhetherTheFormIsThereBeforeItAsksWhetherJQueryIsThere() {
        val script = VtopScripts.PRELOGIN
        val formCheck = script.indexOf("'stdForm not found'")
        val jqCheck = script.indexOf("'jQuery absent'")
        assertTrue(formCheck >= 0, "must still report a missing stdForm")
        assertTrue(jqCheck >= 0, "must still report missing jQuery")
        assertTrue(
            formCheck < jqCheck,
            "a document that is not VTOP's must not be blamed on jQuery - 'jQuery absent' " +
                "was all anyone saw when the handshake failed for three unrelated reasons",
        )
    }

    @Test
    fun preloginAttachesEnoughContextToSeparateTheCausesOfJQueryAbsent() {
        val script = VtopScripts.PRELOGIN
        // 'jQuery absent' on its own cannot say whether the page never loaded, loaded without
        // its own scripts, or is a different document entirely - so every branch carries these.
        for (field in listOf("url:", "title:", "ready:", "body:", "stdForm:", "jqTags:")) {
            assertTrue(script.contains(field), "diag must carry $field")
        }
        assertTrue(
            script.contains("""document.querySelectorAll('script[src*="/jq/js/"]')"""),
            "jqTags must count the tags VTOP actually loads jQuery through",
        )
    }

    @Test
    fun preloginSerializesTheLandingFormAndReportsARefusedRequest() {
        val script = VtopScripts.PRELOGIN
        assertTrue(script.contains("window.jQuery(form).serialize()"))
        assertTrue(script.contains("xhr.open('POST', '/vtop/prelogin/setup', false)"))
        // A refused or timed-out XHR still has status 0 and used to be reported as a success.
        assertTrue(script.contains("xhr.status === 0"))
        assertTrue(script.contains("'prelogin network error'"))
    }

    // ── Source selection ────────────────────────────────────────────────────

    @Test
    fun sourceNameParsingIsForgiving() {
        assertEquals(VtopSource.LOCAL, VtopSource.fromName("local"))
        assertEquals(VtopSource.REMOTE, VtopSource.fromName("REMOTE"))
        assertEquals(VtopSource.LOCAL, VtopSource.fromName(null))
        assertEquals(VtopSource.LOCAL, VtopSource.fromName("garbage"))
    }

    // ── User-Agent pool ─────────────────────────────────────────────────────

    @Test
    fun userAgentPoolContainsOnlyBrowserUAs() {
        val seen = mutableSetOf<String>()
        repeat(40) { seen.add(UserAgentPool.rotate()) }
        assertTrue(seen.size > 1, "rotation should cycle through a pool")
        assertTrue(seen.all { it.startsWith("Mozilla/") }, "all agents must look like browsers")
    }

    @Test
    fun userAgentRotationCyclesWithoutRepeatingAdjacently() {
        val first = UserAgentPool.current()
        val second = UserAgentPool.rotate()
        assertNotEquals(first, second)
    }
}
