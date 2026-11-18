package fit.sigap.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space

enum class ButtonVariant { Primary, Secondary, Ghost, Danger, OnSignal, InkOnSignal, OutlineOnSignal }

enum class ButtonSize(val height: Dp, val horizontal: Dp, val icon: Dp) {
    Small(36.dp, 16.dp, 16.dp),
    Medium(44.dp, 20.dp, 20.dp),
    Large(56.dp, 28.dp, 20.dp),
    Alarm(72.dp, 28.dp, 28.dp),
}

@Composable
fun SigapButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    busy: Boolean = false,
    fullWidth: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val colors = SigapTheme.colors
    val (container, content) = when (variant) {
        ButtonVariant.Primary -> colors.brand to colors.paper0
        ButtonVariant.Secondary -> colors.paper0 to colors.ink950
        ButtonVariant.Ghost -> Color.Transparent to colors.ink700
        ButtonVariant.Danger -> colors.signalRed to colors.paper0
        ButtonVariant.OnSignal -> colors.paper0 to colors.ink950
        ButtonVariant.InkOnSignal -> colors.ink950 to colors.paper0
        ButtonVariant.OutlineOnSignal -> Color.Transparent to colors.paper0
    }
    val textStyle = when (size) {
        ButtonSize.Small -> MaterialTheme.typography.labelMedium
        ButtonSize.Alarm -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.labelLarge
    }
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (container == Color.Transparent) container else container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = if (container == Color.Transparent) 0.5f else 0.85f),
        ),
        border = when (variant) {
            ButtonVariant.Secondary -> BorderStroke(1.dp, colors.line500)
            ButtonVariant.OutlineOnSignal -> BorderStroke(1.dp, colors.paper0)
            else -> null
        },
        contentPadding = PaddingValues(horizontal = size.horizontal),
        elevation = null,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = size.height)
            .semantics { if (busy) stateDescription = "Sedang diproses" },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(size.icon),
                )
            }
            Text(text = text, style = textStyle, textAlign = TextAlign.Center)
        }
    }
}
