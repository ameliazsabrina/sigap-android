package fit.sigap.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.format.formatRelative
import fit.sigap.app.core.format.formatVital
import fit.sigap.app.core.model.PatientVitals
import fit.sigap.app.core.model.VitalReading
import java.time.Instant

@Composable
fun VitalsGrid(vitals: PatientVitals, now: Instant, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        vitals.readings.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                row.forEach { reading ->
                    VitalTile(reading, now, Modifier.weight(1f))
                }
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
        if (vitals.latestInvalid) {
            Text(
                text = "Observasi terbaru tidak valid; angka di atas dari observasi valid sebelumnya.",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = SigapTheme.colors.signalAmber,
            )
        }
    }
}

@Composable
private fun VitalTile(reading: VitalReading, now: Instant, modifier: Modifier = Modifier) {
    val colors = SigapTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.paper50)
            .padding(horizontal = Space.x3, vertical = Space.x2)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(reading.label, style = MaterialTheme.typography.bodySmall, color = colors.ink700)
        Text(
            text = buildAnnotatedString {
                append(reading.value?.let(::formatVital) ?: "—")
                withStyle(
                    SpanStyle(
                        fontSize = SigapTheme.data.xs.fontSize,
                        fontWeight = FontWeight.Normal,
                        color = colors.ink700,
                    ),
                ) { append(" ${reading.unit}") }
            },
            style = SigapTheme.data.xl.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink950,
        )
        Text(
            text = reading.recordedAt?.let { formatRelative(it, now) } ?: "belum ada data valid",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink700,
        )
    }
}
