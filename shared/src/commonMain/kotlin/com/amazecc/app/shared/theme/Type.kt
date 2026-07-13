package com.amazecc.app.shared.theme

import amazecc_app.shared.generated.resources.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

@Composable
fun getOutfitFontFamily() = FontFamily(
    Font(Res.font.outfit_regular, FontWeight.Normal),
    Font(Res.font.outfit_bold, FontWeight.Bold),
    Font(Res.font.outfit_black, FontWeight.Black)
)

@Composable
fun getGeistFontFamily() = FontFamily(
    Font(Res.font.geist_regular, FontWeight.Normal),
    Font(Res.font.geist_medium, FontWeight.Medium),
    Font(Res.font.geist_semibold, FontWeight.SemiBold),
    Font(Res.font.geist_bold, FontWeight.Bold),
    Font(Res.font.geist_black, FontWeight.Black)
)

data class AmazeTypography(
    val display: TextStyle,
    val heading: TextStyle,
    val subheading: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val smallLabel: TextStyle
)

@Composable
fun getAmazeTypography(): AmazeTypography {
    val displayFont = getOutfitFontFamily()
    val bodyFont = getGeistFontFamily()

    return AmazeTypography(
        display = TextStyle(
            fontFamily = displayFont,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            lineHeight = 40.sp
        ),
        heading = TextStyle(
            fontFamily = displayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp
        ),
        subheading = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp
        ),
        body = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        caption = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        smallLabel = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    )
}
