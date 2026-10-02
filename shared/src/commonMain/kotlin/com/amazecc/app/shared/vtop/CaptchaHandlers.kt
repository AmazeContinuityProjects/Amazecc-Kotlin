package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.vtop.captcha.CaptchaEngine

/**
 * Resolves the captcha without user input when the recogniser is confident.
 *
 * VTOP serves either an image captcha or a reCAPTCHA. The recogniser only handles the image
 * form, so a reCAPTCHA always falls through to [delegate] - which on Android is a
 * `grecaptcha.execute()` in the live page, and otherwise manual entry.
 *
 * [forceManual] is the user's opt-in (`SettingsManager.KEY_VTOP_MANUAL_CAPTCHA`, or the toggle on
 * the login screen): when it returns true the recogniser is skipped entirely.
 *
 * [manualAnswer] supplies a captcha the user typed on the login screen *before* submitting. It is
 * consumed once — if VTOP rejects it the dialog takes over for the retry, because a second guess
 * at the same stale image would fail for the same reason.
 */
class AutoCaptchaHandler(
    private val engine: CaptchaEngine = CaptchaEngine(),
    private val delegate: CaptchaHandler? = null,
    private val forceManual: () -> Boolean = { false },
    private val manualAnswer: () -> String? = { null }
) : CaptchaHandler {

    private var usedManualAnswer = false

    override suspend fun onCaptcha(challenge: CaptchaChallenge): CaptchaAnswer {
        if (!challenge.isRecaptcha && !forceManual()) {
            val base64 = challenge.base64
            if (base64 != null && engine.isReady) {
                val prediction = runCatching { engine.solve(base64) }.getOrNull()
                if (prediction != null && prediction.autoSubmit) {
                    return CaptchaAnswer.Text(prediction.text)
                }
            }
        }
        // A captcha typed on the login screen wins over the dialog, but only for the first try.
        if (!challenge.isRecaptcha && !usedManualAnswer) {
            val typed = manualAnswer()?.trim().orEmpty()
            if (typed.isNotBlank()) {
                usedManualAnswer = true
                return CaptchaAnswer.Text(typed)
            }
        }
        return delegate?.onCaptcha(challenge) ?: CaptchaAnswer.Cancelled
    }
}

/**
 * Parks the prompt and lets the UI answer it.
 *
 * Pairs with `Vtop.captchaRequest`, `Vtop.submitCaptcha` and `Vtop.cancelCaptcha`.
 */
object UiCaptchaHandler : CaptchaHandler {
    override suspend fun onCaptcha(challenge: CaptchaChallenge): CaptchaAnswer = Vtop.awaitUiCaptcha(challenge)
}
