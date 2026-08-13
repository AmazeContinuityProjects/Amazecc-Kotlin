package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberUrlOpener(): (url: String) -> Unit = { url ->
    UIApplication.sharedApplication.openURL(NSURL(string = url))
}
