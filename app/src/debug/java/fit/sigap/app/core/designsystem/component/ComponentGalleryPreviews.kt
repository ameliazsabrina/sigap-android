package fit.sigap.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.preview.PreviewTheme

@Preview(name = "Buttons", widthDp = 411, heightDp = 1100, showBackground = true)
@Composable
private fun ButtonGallery() = PreviewTheme {
    val colors = SigapTheme.colors
    Column(
        modifier = Modifier.background(colors.paper50).padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        SectionHeading("Di atas paper")
        SigapButton("Primary", {}, variant = ButtonVariant.Primary, fullWidth = true)
        SigapButton("Secondary", {}, variant = ButtonVariant.Secondary, fullWidth = true)
        SigapButton("Ghost", {}, variant = ButtonVariant.Ghost, fullWidth = true)
        SigapButton("Danger", {}, variant = ButtonVariant.Danger, fullWidth = true)
        SigapButton("Disabled", {}, variant = ButtonVariant.Primary, enabled = false, fullWidth = true)
        SigapButton("Memproses…", {}, variant = ButtonVariant.Primary, busy = true, fullWidth = true)
        SigapButton("Small", {}, size = ButtonSize.Small)
        SigapButton("Large dengan ikon", {}, variant = ButtonVariant.Primary, size = ButtonSize.Large, icon = R.drawable.ic_check)
        OnSignal(colors.signalRed, ButtonVariant.OnSignal)
        OnSignal(colors.signalAmber, ButtonVariant.InkOnSignal)
    }
}

@Composable
private fun OnSignal(background: Color, primary: ButtonVariant) {
    Column(
        modifier = Modifier.background(background).padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        SigapButton("Alarm", {}, variant = primary, size = ButtonSize.Alarm, fullWidth = true, icon = R.drawable.ic_check)
        SigapButton("Outline on signal", {}, variant = ButtonVariant.OutlineOnSignal, size = ButtonSize.Large, fullWidth = true)
        InlineError("Galat di atas signal memakai teks putih.", color = SigapTheme.colors.paper0)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Badges and notices", widthDp = 411, heightDp = 760, showBackground = true)
@Composable
private fun StatusGallery() = PreviewTheme {
    val colors = SigapTheme.colors
    Column(
        modifier = Modifier.background(colors.paper50).padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            BadgeTone.entries.forEach { SigapBadge(it.name, tone = it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            BadgeTone.entries.forEach { SigapBadge(it.name, tone = it, solid = true) }
        }
        SigapBadge("Nurse call", tone = BadgeTone.Violet, icon = R.drawable.ic_bell_ring)
        NoticeStrip("Koneksi terputus · layar diperbarui tiap 10 detik", colors.signalAmber)
        InlineError("Tidak dapat menghubungi server. Menekan tombol lagi mengulang permintaan yang sama.")
        Eyebrow("Code Blue", color = colors.signalRed)
        SigapPanel(title = "Panel", description = "Panel putih di atas paper-50.") {
            DefinitionRow("Nomor incident", "CB-20261117-0007", dataValue = true)
            DefinitionRow("Kode pasien", "RM-SIM-000101", dataValue = true)
        }
    }
}

@Preview(name = "Data states", widthDp = 411, heightDp = 640, showBackground = true)
@Composable
private fun DataStateGallery() = PreviewTheme {
    Column(modifier = Modifier.background(SigapTheme.colors.paper50)) {
        LoadingState(label = "Memeriksa alert")
        ErrorState("Tidak dapat menghubungi server. Periksa koneksi HP.", onRetry = {})
    }
}
