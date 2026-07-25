package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.WebKit.WKWebView
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun LatexViewer(latex: String, modifier: Modifier) {
    UIKitView(
        factory = {
            WKWebView().apply {
                setOpaque(false)
                backgroundColor = platform.UIKit.UIColor.clearColor
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <script type="text/javascript" async
                          src="https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.7/MathJax.js?config=TeX-MML-AM_CHTML">
                        </script>
                        <style>
                            body { font-family: -apple-system, sans-serif; color: #E0E0E0; background-color: transparent; font-size: 16px; margin: 0; padding: 0; }
                        </style>
                    </head>
                    <body>
                        $latex
                    </body>
                    </html>
                """.trimIndent()
                loadHTMLString(html, baseURL = null)
            }
        },
        modifier = modifier,
        update = { view ->
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <script type="text/javascript" async
                      src="https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.7/MathJax.js?config=TeX-MML-AM_CHTML">
                    </script>
                    <style>
                        body { font-family: -apple-system, sans-serif; color: #E0E0E0; background-color: transparent; font-size: 16px; margin: 0; padding: 0; }
                    </style>
                </head>
                <body>
                    $latex
                </body>
                </html>
            """.trimIndent()
            view.loadHTMLString(html, baseURL = null)
        }
    )
}
