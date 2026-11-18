package fit.sigap.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.theme.SigapColors
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.tint

enum class BadgeTone { Neutral, Brand, Amber, Red, Violet, Green }

private fun BadgeTone.signal(colors: SigapColors): Color = when (this) {
    BadgeTone.Neutral -> colors.ink700
    BadgeTone.Brand -> colors.brand
    BadgeTone.Amber -> colors.signalAmber
    BadgeTone.Red -> colors.signalRed
    BadgeTone.Violet -> colors.signalViolet
    BadgeTone.Green -> colors.signalGreen
}

@Composable
fun SigapBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
    solid: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val colors = SigapTheme.colors
    val signal = tone.signal(colors)
    val content = if (solid) colors.paper0 else signal
    Row(
        modifier = modifier
            .then(
                if (solid) {
                    Modifier.background(signal, CircleShape)
                } else {
                    Modifier.background(signal.tint(), CircleShape).border(1.dp, signal.tint(0.25f), CircleShape)
                },
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}
