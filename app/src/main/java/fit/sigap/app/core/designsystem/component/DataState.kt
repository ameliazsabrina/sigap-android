package fit.sigap.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.designsystem.theme.tint

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Memuat data") {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x6, vertical = Space.x12)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = SigapTheme.colors.ink700,
            strokeWidth = 2.dp,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SigapTheme.colors.ink700)
    }
}

@Composable
fun ErrorState(
    description: String,
    modifier: Modifier = Modifier,
    title: String = "Data gagal dimuat",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x6, vertical = Space.x12)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(SigapTheme.colors.signalRed.tint(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_circle_alert),
                contentDescription = null,
                tint = SigapTheme.colors.signalRed,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = SigapTheme.colors.ink950)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = SigapTheme.colors.ink700,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.x1),
            )
        }
        if (onRetry != null) {
            SigapButton("Coba lagi", onRetry, size = ButtonSize.Medium)
        }
    }
}

@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
    color: Color = SigapTheme.colors.signalRed,
    prominent: Boolean = false,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            painterResource(R.drawable.ic_circle_alert),
            null,
            tint = color,
            modifier = Modifier.padding(top = 2.dp).size(if (prominent) 20.dp else 16.dp),
        )
        Text(
            text = message,
            style = if (prominent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
fun NoticeStrip(text: String, tone: Color, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = SigapTheme.colors.ink950,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.tint(0.15f))
            .border(1.dp, tone.tint(0.3f), shape)
            .padding(horizontal = Space.x3, vertical = Space.x2)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

