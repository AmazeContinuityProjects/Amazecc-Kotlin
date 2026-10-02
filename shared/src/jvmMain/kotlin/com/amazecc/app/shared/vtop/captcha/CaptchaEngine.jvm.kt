package com.amazecc.app.shared.vtop.captcha

/**
 * Desktop stub: the recogniser's weights are bundled in the Android assets and the decode path
 * went through `android.graphics`. Reporting `isReady = false` makes `AutoCaptchaHandler` fall
 * straight through to manual entry, which is the correct desktop behaviour - there is no way to
 * solve a captcha here anyway.
 */
actual class CaptchaEngine {
    actual val isReady: Boolean = false

    actual suspend fun solve(dataUri: String): CaptchaPrediction? = null
}
