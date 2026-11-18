package fit.sigap.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class SigapColors(
    val ink950: Color = Color(0xFF0F1115),
    val ink700: Color = Color(0xFF4A505C),
    val paper50: Color = Color(0xFFEEF0F4),
    val paper0: Color = Color(0xFFFFFFFF),
    val line200: Color = Color(0xFFE2E5EB),
    val line500: Color = Color(0xFF7B818D),
    val brand: Color = Color(0xFF1460F0),
    val signalAmber: Color = Color(0xFFA76600),
    val signalRed: Color = Color(0xFFB42318),
    val signalViolet: Color = Color(0xFF6A3FC8),
    val signalGreen: Color = Color(0xFF247A4D),
)

fun Color.tint(alpha: Float = 0.10f): Color = copy(alpha = alpha)
