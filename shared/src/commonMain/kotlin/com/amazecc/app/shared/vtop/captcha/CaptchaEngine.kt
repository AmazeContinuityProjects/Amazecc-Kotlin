package com.amazecc.app.shared.vtop.captcha

/** Result of running the recogniser over a captcha image. */
data class CaptchaPrediction(
    val text: String,
    val meanConfidence: Float,
    val perCharacterConfidence: List<Float> = emptyList()
) {
    /** True when the prediction is confident enough to submit without asking the user. */
    val autoSubmit: Boolean
        get() = meanConfidence >= CaptchaMath.CONFIDENCE_THRESHOLD

    val highConfidence: Boolean
        get() = meanConfidence >= CaptchaMath.HIGH_CONFIDENCE_THRESHOLD
}

/**
 * Recognises VTOP's image captcha.
 *
 * Android-only: image decode and bilinear resize need `android.graphics.Bitmap`. The iOS actual
 * is a stub, consistent with the rest of the local VTOP layer.
 */
expect class CaptchaEngine() {

    /** True when the weights asset is present and the platform can decode images. */
    val isReady: Boolean

    /**
     * Recognises the captcha in a `data:image/...;base64,` URI.
     *
     * Returns null when the image cannot be decoded or the model is unavailable, in which case
     * the caller should fall back to manual entry.
     */
    suspend fun solve(dataUri: String): CaptchaPrediction?
}
