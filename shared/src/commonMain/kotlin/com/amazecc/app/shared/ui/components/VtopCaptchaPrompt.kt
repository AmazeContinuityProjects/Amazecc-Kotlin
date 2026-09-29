package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.vtop.CaptchaChallenge
import com.amazecc.app.shared.vtop.Vtop
import com.amazecc.app.shared.vtop.decodeDataUriToImageBitmap

/**
 * Manual captcha prompt for the on-device VTOP login.
 *
 * Shown only when [com.amazecc.app.shared.vtop.AutoCaptchaHandler] is not confident enough to
 * submit by itself, or when VTOP escalates to a reCAPTCHA. Answers go through
 * [Vtop.submitCaptcha] / [Vtop.cancelCaptcha], which unblock the parked login coroutine.
 */
@Composable
fun VtopCaptchaPrompt(challenge: CaptchaChallenge) {
    val colors = AmazeTheme.colors
    var entry by remember(challenge.base64) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { Vtop.cancelCaptcha() },
        confirmButton = {
            AmazeButton(
                text = "Submit",
                onClick = { Vtop.submitCaptcha(entry) },
                enabled = entry.isNotBlank()
            )
        },
        dismissButton = {
            TextButton(onClick = { Vtop.cancelCaptcha() }) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        title = {
            Text("Enter the captcha", fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.md)
            ) {
                if (challenge.isRecaptcha) {
                    Text(
                        "VTOP is asking for a reCAPTCHA. Solve it in the page, then submit.",
                        color = colors.textSecondary
                    )
                }
                challenge.base64?.let { dataUri ->
                    val image = remember(dataUri) { decodeDataUriToImageBitmap(dataUri) }
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = "Captcha",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 72.dp)
                                .padding(vertical = AmazeTheme.spacing.xs)
                        )
                    }
                }
                AmazeTextField(
                    value = entry,
                    onValueChange = { entry = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                    label = "Captcha",
                    placeholder = "6 characters",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(AmazeTheme.radius.large)
    )
}
