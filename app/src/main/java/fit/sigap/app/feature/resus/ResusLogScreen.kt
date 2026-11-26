package fit.sigap.app.feature.resus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fit.sigap.app.core.designsystem.component.BackLink
import fit.sigap.app.core.designsystem.component.BadgeTone
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.DefinitionRow
import fit.sigap.app.core.designsystem.component.Eyebrow
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.NoticeStrip
import fit.sigap.app.core.designsystem.component.SectionHeading
import fit.sigap.app.core.designsystem.component.SigapBadge
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapTextField
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.format.formatElapsed
import fit.sigap.app.core.format.formatTime
import fit.sigap.app.core.model.ResusAction
import fit.sigap.app.core.model.ResusDelivery
import fit.sigap.app.core.model.ResusDrug
import fit.sigap.app.core.model.ResusOutcome
import fit.sigap.app.core.model.ResusProcedure
import fit.sigap.app.core.model.ResusSheet
import fit.sigap.app.core.model.ResusSheetEntry
import fit.sigap.app.core.model.Rhythm
import fit.sigap.app.core.model.conflictsWith
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import java.time.Duration
import java.time.Instant

private val TileHeight = 72.dp
private const val OTHER_ACTION_MAX = 120

sealed interface ResusAccess {
    data object Write : ResusAccess

    data class ReadOnly(val reason: String) : ResusAccess
}

enum class ResusFinish {
    ARRIVE_FIRST,
    RESOLVE,
    DOCUMENT,
    VIEW_DOCUMENTATION,
}

data class ResusLogUiState(
    val locationName: String,
    val t0: Instant,
    val log: ResusLogState,
    val access: ResusAccess,
    val outboxError: String? = null,
    val canMarkArrived: Boolean = false,
    val arrivePhase: CommandPhase = CommandPhase.Idle,
    val finish: ResusFinish? = null,
    val resolvePhase: CommandPhase = CommandPhase.Idle,
) {
    val sheet: ResusSheet get() = log.sheet

    val showInput: Boolean get() = access == ResusAccess.Write
    val pendingCount: Int get() = log.log.entries.count { it.delivery == ResusDelivery.PENDING }

    val showFinish: Boolean
        get() = finish != null && (sheet.outcome != null || finish == ResusFinish.DOCUMENT || finish == ResusFinish.VIEW_DOCUMENTATION)
}

@Stable
interface ResusLogActions {
    fun onBack()
    fun onRhythm(rhythm: Rhythm)
    fun onRecord(action: ResusAction)
    fun onConfirmPrompt()
    fun onDismissPrompt()
    fun onUndo()
    fun onRequestVoid(entryId: String)
    fun onArrive()
    fun onResolve()
    fun onDocument()
    fun onRetry()
}

@Composable
fun ResusLogScreen(state: ResusLogUiState, now: Instant, actions: ResusLogActions) {
    KeepScreenOn()
    val undo = state.log.undo
    AppScreen(
        gap = Space.x4,
        bottomBar = { if (undo != null && state.showInput) UndoBar(undo.label, actions::onUndo) },
    ) {
        BackLink("Incident", actions::onBack)
        Header(state, now)
        AccessLine(state.access)
        SyncLine(state, actions)

        if (state.showInput) {
            RhythmPicker(state.sheet.rhythm, actions::onRhythm)
            ActionGrid(state.sheet, actions)
            if (state.canMarkArrived) {
                SigapButton(
                    text = if (state.arrivePhase.isBusy) "Memproses…" else "Tim tiba",
                    onClick = actions::onArrive,
                    size = ButtonSize.Large,
                    fullWidth = true,
                    busy = state.arrivePhase.isBusy,
                )
                (state.arrivePhase as? CommandPhase.Failed)?.let { InlineError(it.message) }
            }
            OutcomePicker(state.sheet.outcome, actions)
        }
        state.log.saveError?.let { InlineError(it, prominent = true) }
        if (state.showFinish) FinishResponse(state, actions)

        ProtocolNotice()
        Recap(state.sheet, state.t0, now)
        LogList(state.sheet, canCorrect = state.showInput, onVoid = actions::onRequestVoid)
    }

    state.log.prompt?.let { PromptDialog(it, now, actions) }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun Header(state: ResusLogUiState, now: Instant) {
    val colors = SigapTheme.colors
    val sheet = state.sheet
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        Eyebrow("Log tindakan resusitasi", color = colors.signalRed)
        Text(
            text = state.locationName,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink950,
            modifier = Modifier.semantics { heading() },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
        TimerCell(
            label = if (sheet.outcomeAt != null) "Total · berhenti" else "Total sejak panggilan",
            value = formatElapsed(sheet.totalElapsed(state.t0, now)),
        )
        TimerCell(
            label = if (sheet.cycleCount == 0) "Siklus CPR" else "Siklus ke-${sheet.cycleCount}",
            value = sheet.cycleElapsed(now)?.let(::formatElapsed) ?: "--:--",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
        val rhythm = sheet.rhythm
        if (rhythm == null) {
            SigapBadge("Irama belum dicatat")
        } else {
            SigapBadge(
                "${rhythm.label} · ${if (rhythm.shockable) "shockable" else "non-shockable"}",
                tone = if (rhythm.shockable) BadgeTone.Red else BadgeTone.Violet,
            )
        }
        sheet.outcome?.let { SigapBadge("Status akhir: ${it.label}", tone = BadgeTone.Neutral, solid = true) }
    }
}

@Composable
private fun RowScope.TimerCell(label: String, value: String) {
    val colors = SigapTheme.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.large)
            .background(colors.paper0)
            .padding(Space.x3)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.ink700)
        Text(value, style = SigapTheme.data.timer, color = colors.ink950)
    }
}

@Composable
private fun AccessLine(access: ResusAccess) {
    val colors = SigapTheme.colors
    Text(
        text = when (access) {
            ResusAccess.Write -> "Setiap HP tim dapat mencatat. Pencatat bergantian tiap siklus; HP yang dipegang langsung dipakai."
            is ResusAccess.ReadOnly -> access.reason
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.ink700,
    )
}

@Composable
private fun FinishResponse(state: ResusLogUiState, actions: ResusLogActions) {
    val colors = SigapTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SectionHeading(
            "Selesaikan respons",
            description = "Status akhir di log tidak menutup incident. Tutup respons, lalu isi dokumentasi akhir.",
        )
        when (state.finish) {
            ResusFinish.ARRIVE_FIRST -> Text(
                text = "Tekan \"Tim tiba\" lebih dulu, lalu nyatakan penanganan selesai.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink950,
            )
            ResusFinish.RESOLVE -> {
                val phase = state.resolvePhase
                SigapButton(
                    text = if (phase.isBusy) "Memproses…" else "Nyatakan penanganan selesai",
                    onClick = actions::onResolve,
                    size = ButtonSize.Large,
                    fullWidth = true,
                    busy = phase.isBusy,
                )
                if (phase is CommandPhase.Failed) {
                    InlineError(phase.message)
                } else {
                    Text(
                        text = "Bukan pernyataan tentang kondisi pasien. Log ditutup untuk ketukan baru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink700,
                    )
                }
            }
            ResusFinish.DOCUMENT -> SigapButton(
                text = "Isi dokumentasi akhir",
                onClick = actions::onDocument,
                variant = ButtonVariant.Primary,
                size = ButtonSize.Large,
                fullWidth = true,
            )
            ResusFinish.VIEW_DOCUMENTATION -> SigapButton(
                text = "Lihat dokumentasi",
                onClick = actions::onDocument,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Medium,
            )
            null -> Unit
        }
    }
}

@Composable
private fun ProtocolNotice() {
    NoticeStrip(
        text = "Pengingat protokol · keputusan pada team leader. Interval, obat, dosis, dan energi belum punya " +
            "sumber yang diverifikasi, jadi tidak ditampilkan.",
        tone = SigapTheme.colors.line500,
    )
}

@Composable
private fun SyncLine(state: ResusLogUiState, actions: ResusLogActions) {
    val colors = SigapTheme.colors
    val syncError = state.log.syncError
    when {
        syncError != null -> Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            InlineError("Log dari HP lain belum diperbarui: $syncError", color = colors.signalAmber)
            SigapButton("Coba lagi", actions::onRetry, size = ButtonSize.Small)
        }
        !state.log.synced -> Text("Memuat log dari server…", style = MaterialTheme.typography.bodySmall, color = colors.ink700)
    }
    if (state.pendingCount > 0) {
        val reason = state.outboxError?.let { " Terakhir gagal: $it" }.orEmpty()
        Text(
            text = "${state.pendingCount} tindakan tersimpan di HP ini, belum terkirim. Dikirim otomatis.$reason",
            style = MaterialTheme.typography.bodySmall,
            color = colors.signalAmber,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun RhythmPicker(current: Rhythm?, onRhythm: (Rhythm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SectionHeading("Irama", description = "Mengubah irama tidak memulai siklus baru.")
        RhythmGroup("Shockable", listOf(Rhythm.VF, Rhythm.VT), current, onRhythm)
        RhythmGroup("Non-shockable", listOf(Rhythm.ASYSTOLE, Rhythm.PEA), current, onRhythm)
    }
}

@Composable
private fun RhythmGroup(title: String, rhythms: List<Rhythm>, current: Rhythm?, onRhythm: (Rhythm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = SigapTheme.colors.ink700)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            rhythms.forEach { rhythm ->
                Tile(
                    label = rhythm.label,
                    onClick = { onRhythm(rhythm) },
                    selected = rhythm == current,
                )
            }
        }
    }
}

@Composable
private fun ActionGrid(sheet: ResusSheet, actions: ResusLogActions) {
    var other by rememberSaveable { mutableStateOf("") }
    val dimmedNote = "Irama terakhir non-shockable"
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SectionHeading("Tindakan", description = "Satu ketukan, langsung tersimpan di HP ini.")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            Tile("Mulai siklus CPR", { actions.onRecord(ResusAction.CprCycleStarted) }, count = sheet.cycleCount)
            Tile("Epinefrin", { actions.onRecord(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE)) }, count = sheet.epinephrineCount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            val shock = ResusAction.ShockDelivered
            val amiodarone = ResusAction.DrugGiven(ResusDrug.AMIODARONE)
            Tile(
                "Defibrilasi",
                { actions.onRecord(shock) },
                count = sheet.shockCount,
                dimmedNote = dimmedNote.takeIf { shock.conflictsWith(sheet.rhythm) },
            )
            Tile(
                "Amiodarone",
                { actions.onRecord(amiodarone) },
                count = sheet.amiodaroneCount,
                dimmedNote = dimmedNote.takeIf { amiodarone.conflictsWith(sheet.rhythm) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            Tile("ETT", { actions.onRecord(ResusAction.ProcedureDone(ResusProcedure.ETT)) })
            Tile("Akses IV/IO", { actions.onRecord(ResusAction.ProcedureDone(ResusProcedure.IV_IO_ACCESS)) })
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            SigapTextField(
                label = "Tindakan lain",
                value = other,
                onValueChange = { other = it },
                maxLength = OTHER_ACTION_MAX,
                modifier = Modifier.weight(1f),
            )
            SigapButton(
                text = "Catat",
                onClick = {
                    actions.onRecord(ResusAction.ProcedureDone(ResusProcedure.OTHER, other.trim()))
                    other = ""
                },
                enabled = other.isNotBlank(),
                size = ButtonSize.Large,
            )
        }
    }
}

@Composable
private fun OutcomePicker(current: ResusOutcome?, actions: ResusLogActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SectionHeading(
            "Status akhir",
            description = "Menghentikan timer. Bukan \"Respons selesai\": incident tetap terbuka sampai tombol itu ditekan.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            ResusOutcome.entries.forEach { outcome ->
                Tile(outcome.label, { actions.onRecord(ResusAction.OutcomeRecorded(outcome)) }, selected = outcome == current)
            }
        }
    }
}

@Composable
private fun RowScope.Tile(
    label: String,
    onClick: () -> Unit,
    count: Int = 0,
    selected: Boolean = false,
    dimmedNote: String? = null,
) {
    val colors = SigapTheme.colors
    val dimmed = dimmedNote != null
    val container = when {
        selected -> colors.brand
        dimmed -> colors.paper50
        else -> colors.paper0
    }
    val content = when {
        selected -> colors.paper0
        dimmed -> colors.ink700
        else -> colors.ink950
    }
    Surface(
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, if (selected) colors.brand else colors.line500),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = TileHeight)
            .semantics {
                if (selected) this.selected = true
                if (dimmedNote != null) stateDescription = "$dimmedNote, minta konfirmasi"
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.x3, vertical = Space.x2),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                if (count > 0) Text("×$count", style = SigapTheme.data.md)
            }
            if (dimmedNote != null) Text(dimmedNote, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Recap(sheet: ResusSheet, t0: Instant, now: Instant) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(SigapTheme.colors.paper0)
            .padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        SectionHeading("Rekap", description = "Dihitung dari log tindakan, bukan kesimpulan sistem.")
        DefinitionRow("Durasi", formatElapsed(sheet.totalElapsed(t0, now)), dataValue = true)
        DefinitionRow("Siklus CPR", sheet.cycleCount.toString(), dataValue = true)
        DefinitionRow("Epinefrin", "${sheet.epinephrineCount}×", dataValue = true)
        DefinitionRow("Defibrilasi", "${sheet.shockCount}×", dataValue = true)
        DefinitionRow("Amiodarone", "${sheet.amiodaroneCount}×", dataValue = true)
        DefinitionRow("Tindakan lain", sheet.procedures.joinToString(", ").ifEmpty { "Tidak ada" })
        DefinitionRow("Urutan irama", sheet.rhythmSequence.joinToString(" → ") { it.label }.ifEmpty { "Belum dicatat" })
        sheet.outcome?.let { DefinitionRow("Status akhir", it.label) }
    }
}

@Composable
private fun LogList(sheet: ResusSheet, canCorrect: Boolean, onVoid: (String) -> Unit) {
    val colors = SigapTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SectionHeading("Log tindakan", description = "Terbaru di atas. Koreksi mencoret entri, tidak menghapusnya.")
        val rows = sheet.entries.asReversed()
        if (rows.isEmpty()) {
            Text("Belum ada tindakan tercatat.", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.paper0),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = colors.line200)
                LogRow(row, sheet, canCorrect, onVoid)
            }
        }
    }
}

@Composable
private fun LogRow(row: ResusSheetEntry, sheet: ResusSheet, canCorrect: Boolean, onVoid: (String) -> Unit) {
    val colors = SigapTheme.colors
    val entry = row.entry
    val action = entry.action
    val correctable = canCorrect && !row.voided && entry.delivery != ResusDelivery.REJECTED &&
        action !is ResusAction.Unrecognized
    val detail = when (action) {
        is ResusAction.Correction -> sheet.entries.firstOrNull { it.entry.id == action.voidsEntryId }
            ?.let { "Membatalkan: ${it.entry.action.label}" }
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = Space.x4, vertical = Space.x2),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatTime(entry.tappedAt), style = SigapTheme.data.sm, color = colors.ink700)
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { if (row.voided) stateDescription = "Dibatalkan" },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.voided) colors.ink700 else colors.ink950,
                textDecoration = if (row.voided) TextDecoration.LineThrough else null,
            )
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.ink700) }
            Text(
                text = listOfNotNull(
                    entry.actorName ?: "anggota tim",
                    "belum terkirim".takeIf { entry.delivery == ResusDelivery.PENDING },
                    "jam HP diragukan".takeIf { entry.tappedAtSuspect },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.delivery == ResusDelivery.PENDING || entry.tappedAtSuspect) colors.signalAmber else colors.ink700,
            )
            entry.rejectedReason?.let { InlineError("Ditolak server: $it") }
        }
        if (correctable) {
            Text(
                text = if (action is ResusAction.Correction) "Batalkan koreksi" else "Koreksi",
                style = MaterialTheme.typography.labelLarge,
                color = colors.brand,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button) { onVoid(entry.id) }
                    .padding(horizontal = Space.x2, vertical = Space.x3),
            )
        }
    }
}

@Composable
private fun UndoBar(label: String, onUndo: () -> Unit) {
    val colors = SigapTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ink950)
            .navigationBarsPadding()
            .padding(horizontal = Space.x4, vertical = Space.x2)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label tercatat", style = MaterialTheme.typography.bodyLarge, color = colors.paper0, modifier = Modifier.weight(1f))
        SigapButton("Urungkan", onUndo, variant = ButtonVariant.OutlineOnSignal, size = ButtonSize.Large)
    }
}

@Composable
private fun PromptDialog(prompt: ResusPrompt, now: Instant, actions: ResusLogActions) {
    val colors = SigapTheme.colors
    val (title, body, confirm) = when (prompt) {
        is ResusPrompt.Duplicate -> {
            val seconds = Duration.between(prompt.previous.tappedAt, now).seconds.coerceAtLeast(0)
            val by = prompt.previous.actorName ?: "anggota tim"
            Triple(
                "Catat lagi?",
                "${prompt.previous.action.label} sudah dicatat $seconds detik lalu oleh $by.",
                "Catat lagi",
            )
        }
        is ResusPrompt.RhythmConflict -> Triple(
            "Irama terakhir ${prompt.rhythm.label}",
            "Irama terakhir yang dicatat non-shockable. Catat ${prompt.action.label} tetap?",
            "Tetap catat",
        )
        is ResusPrompt.Void -> Triple(
            "Batalkan entri ini?",
            "\"${prompt.entry.action.label}\" ${formatTime(prompt.entry.tappedAt)} akan dicoret. Entri tetap terlihat di log.",
            "Batalkan entri",
        )
    }
    AlertDialog(
        onDismissRequest = actions::onDismissPrompt,
        containerColor = colors.paper0,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink950) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.ink950) },
        confirmButton = {
            SigapButton(
                confirm,
                actions::onConfirmPrompt,
                variant = if (prompt is ResusPrompt.Void) ButtonVariant.Danger else ButtonVariant.Primary,
                size = ButtonSize.Large,
            )
        },
        dismissButton = { SigapButton("Jangan", actions::onDismissPrompt, variant = ButtonVariant.Ghost, size = ButtonSize.Large) },
    )
}
