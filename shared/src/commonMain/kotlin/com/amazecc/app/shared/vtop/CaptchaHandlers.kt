package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.vtop.captcha.CaptchaEngine

/**
 * Resolves the captcha without user input when the recogniser is confident.
 *
 * VTOP serves either an image captcha or a reCAPTCHA. The recogniser only handles the image
 * form, so a reCAPTCHA always falls through to [delegate] — which on Android is a
 * `grecaptcha.execute()` in the live page, and otherwise manual entry.
 */
class AutoCaptchaHandler(
    private val engine: CaptchaEngine = CaptchaEngine(),
    private val delegate: CaptchaHandler? = null
) : CaptchaHandler {

    override suspend fun onCaptcha(challenge: CaptchaChallenge): CaptchaAnswer {
        if (!challenge.isRecaptcha) {
            val base64 = challenge.base64
            if (base64 != null && engine.isReady) {
                val prediction = runCatching { engine.solve(base64) }.getOrNull()
                if (prediction != null && prediction.autoSubmit) {
                    return CaptchaAnswer.Text(prediction.text)
                }
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
