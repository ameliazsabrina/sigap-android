package fit.sigap.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fit.sigap.app.R

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val PlusJakartaSans = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

val DataFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

private val BaseStyle = TextStyle(
    fontFamily = PlusJakartaSans,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    BaseStyle.copy(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
    )

val SigapTypography = Typography(
    displayLarge = style(36, 44, FontWeight.SemiBold, -0.01),
    displayMedium = style(32, 38, FontWeight.SemiBold, -0.01),
    displaySmall = style(30, 36, FontWeight.SemiBold, -0.01),
    headlineLarge = style(32, 38, FontWeight.SemiBold, -0.01),
    headlineMedium = style(24, 30, FontWeight.SemiBold),
    headlineSmall = style(24, 30, FontWeight.Bold),
    titleLarge = style(18, 24, FontWeight.Bold),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20),
    bodySmall = style(12, 16),
    labelLarge = style(16, 24, FontWeight.SemiBold),
    labelMedium = style(14, 20, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.SemiBold),
)

@Immutable
data class SigapDataTypography(
    val xl: TextStyle,
    val lg: TextStyle,
    val md: TextStyle,
    val sm: TextStyle,
    val xs: TextStyle,
)

private fun dataStyle(size: Int, line: Int) = BaseStyle.copy(
    fontFamily = DataFontFamily,
    fontFeatureSettings = "tnum",
    fontSize = size.sp,
    lineHeight = line.sp,
)

val DefaultDataTypography = SigapDataTypography(
    xl = dataStyle(24, 30),
    lg = dataStyle(18, 24),
    md = dataStyle(16, 24),
    sm = dataStyle(14, 20),
    xs = dataStyle(12, 16),
)
