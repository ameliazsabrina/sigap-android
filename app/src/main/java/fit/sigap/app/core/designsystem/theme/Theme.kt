package fit.sigap.app.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp
    val x16 = 64.dp
}

private val SigapShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalSigapColors = staticCompositionLocalOf { SigapColors() }
private val LocalDataTypography = staticCompositionLocalOf { DefaultDataTypography }
val LocalReducedMotion = staticCompositionLocalOf { false }

object SigapTheme {
    val colors: SigapColors
        @Composable @ReadOnlyComposable
        get() = LocalSigapColors.current

    val data: SigapDataTypography
        @Composable @ReadOnlyComposable
        get() = LocalDataTypography.current
}

@Composable
fun SigapAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val colors = SigapColors()
    val scheme = lightColorScheme(
        primary = colors.brand,
        onPrimary = colors.paper0,
        primaryContainer = colors.brand.tint(),
        onPrimaryContainer = colors.brand,
        secondary = colors.ink950,
        onSecondary = colors.paper0,
        secondaryContainer = colors.brand.tint(),
        onSecondaryContainer = colors.brand,
        tertiary = colors.signalViolet,
        onTertiary = colors.paper0,
        error = colors.signalRed,
        onError = colors.paper0,
        errorContainer = colors.signalRed.tint(),
        onErrorContainer = colors.signalRed,
        background = colors.paper50,
        onBackground = colors.ink950,
        surface = colors.paper0,
        onSurface = colors.ink950,
        surfaceVariant = colors.paper50,
        onSurfaceVariant = colors.ink700,
        surfaceTint = colors.paper0,
        outline = colors.line500,
        outlineVariant = colors.line200,
        surfaceContainerLowest = colors.paper0,
        surfaceContainerLow = colors.paper0,
        surfaceContainer = colors.paper0,
        surfaceContainerHigh = colors.paper0,
        surfaceContainerHighest = colors.paper50,
        surfaceBright = colors.paper0,
        surfaceDim = colors.paper50,
    )
    CompositionLocalProvider(
        LocalSigapColors provides colors,
        LocalDataTypography provides DefaultDataTypography,
        LocalReducedMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SigapTypography,
            shapes = SigapShapes,
            content = content,
        )
    }
}
