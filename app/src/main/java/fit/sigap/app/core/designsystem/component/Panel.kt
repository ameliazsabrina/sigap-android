package fit.sigap.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.designsystem.theme.tint

@Composable
fun SigapPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    padding: Dp = Space.x5,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(SigapTheme.colors.paper0)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        if (title != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = SigapTheme.colors.ink950,
                    modifier = Modifier.semantics { heading() },
                )
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = SigapTheme.colors.ink700)
                }
            }
        }
        content()
    }
}

@Composable
fun OutlinedCard(
    borderColor: Color,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SigapTheme.colors.paper0)
            .border(borderWidth, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
        content = content,
    )
}

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = SigapTheme.colors.ink950,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = SigapTheme.colors.ink700)
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = SigapTheme.colors.ink700) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun BackLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Kembali ke $label", onClick = onClick)
            .padding(end = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_left),
            contentDescription = null,
            tint = SigapTheme.colors.ink700,
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = SigapTheme.colors.ink700)
    }
}

@Composable
fun DefinitionRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    dataValue: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SigapTheme.colors.ink700,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (dataValue) SigapTheme.data.sm else MaterialTheme.typography.bodyMedium,
            color = SigapTheme.colors.ink950,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun IconTile(
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(color.tint()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SigapLogo(modifier: Modifier = Modifier, size: Dp = 56.dp, withName: Boolean = false) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Image(
            painter = painterResource(R.drawable.sigap_logo),
            contentDescription = if (withName) null else "SIGAP",
            modifier = Modifier
                .size(size)
                .semantics { if (withName) contentDescription = "" },
        )
        if (withName) {
            Text(
                text = "SIGAP",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                color = SigapTheme.colors.ink950,
            )
        }
    }
}
