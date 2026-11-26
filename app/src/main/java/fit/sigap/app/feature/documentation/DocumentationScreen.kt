package fit.sigap.app.feature.documentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.BackLink
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.CheckRow
import fit.sigap.app.core.designsystem.component.ChoiceOption
import fit.sigap.app.core.designsystem.component.DefinitionRow
import fit.sigap.app.core.designsystem.component.Eyebrow
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.NoticeStrip
import fit.sigap.app.core.designsystem.component.MultiChoiceChips
import fit.sigap.app.core.designsystem.component.RadioList
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.component.SigapTextField
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.format.formatDuration
import fit.sigap.app.core.format.formatTime
import fit.sigap.app.core.model.CodeBlueForm
import fit.sigap.app.core.model.CodeBlueFormLimits
import fit.sigap.app.core.model.CodeBlueFormRow
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.ResponseAnswer
import fit.sigap.app.core.model.TextAnswer
import fit.sigap.app.feature.common.AppScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HourMinute = DateTimeFormatter.ofPattern("HH:mm")

private val FailureTypes = listOf(ChoiceOption("JANTUNG", "Jantung"), ChoiceOption("NAFAS", "Nafas"))
private val Airway = listOf(
    ChoiceOption("ORAL", "Oral"),
    ChoiceOption("ENDOTRACHEAL", "Endotracheal"),
    ChoiceOption("TRACHEOSTOMY", "Tracheostomy"),
)
private val Breathing = listOf(
    ChoiceOption("MOUTH_TO_MOUTH", "Mouth to mouth"),
    ChoiceOption("BMV_TO_MOUTH", "BMV to mouth"),
    ChoiceOption("BAG_TO_ETT", "Bag to ETT"),
)
private val Rhythm = listOf(
    ChoiceOption("VT", "VT"),
    ChoiceOption("VF", "VF"),
    ChoiceOption("ASYSTOLE", "Asystole"),
    ChoiceOption("PEA", "PEA"),
)
private val Outcomes = listOf(ChoiceOption("ROSC", "ROSC"), ChoiceOption("MENINGGAL", "Meninggal"))
private val Answers = listOf(
    ChoiceOption(ResponseAnswer.YES, "Ya, sudah selesai"),
    ChoiceOption(ResponseAnswer.NO, "Belum atau tidak selesai"),
    ChoiceOption(ResponseAnswer.UNKNOWN, "Tidak diketahui"),
)

val DocumentationValuesSaver: Saver<DocumentationValues, String> = Saver(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<DocumentationValues>(it) },
)

enum class DocumentationPending { SAVE, COMPLETE }

data class DocumentationUiState(
    val incident: Incident,
    val statusLine: String,
    val canWrite: Boolean,
    val pending: DocumentationPending? = null,
    val message: String? = null,
    val error: String? = null,
    val versionConflict: Boolean = false,
    val draftedFromLog: Boolean = false,
)

interface DocumentationActions {
    fun onBack()
    fun onSave(values: DocumentationValues)
    fun onComplete(values: DocumentationValues)
    fun onReloadAfterConflict()
    fun onOverwriteAfterConflict(values: DocumentationValues)
}

@Composable
fun DocumentationScreen(
    state: DocumentationUiState,
    initialValues: DocumentationValues,
    actions: DocumentationActions,
) {
    val colors = SigapTheme.colors
    var values by rememberSaveable(stateSaver = DocumentationValuesSaver) { mutableStateOf(initialValues) }
    var answerError by rememberSaveable { mutableStateOf<String?>(null) }

    AppScreen(
        bottomBar = {
            if (state.canWrite) {
                DocumentationBar(
                    state = state,
                    answerError = answerError,
                    onSave = { actions.onSave(values) },
                    onComplete = {
                        if (values.answer == null) {
                            answerError = "Wajib dijawab. Pilih \"Tidak diketahui\" bila memang tidak diketahui."
                        } else {
                            actions.onComplete(values)
                        }
                    },
                    onReload = actions::onReloadAfterConflict,
                    onOverwrite = { actions.onOverwriteAfterConflict(values) },
                )
            }
        },
    ) {
        BackLink("Incident", actions::onBack)

        Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Eyebrow(state.incident.number)
            Text(
                text = "Dokumentasi pascakejadian",
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink950,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "${state.incident.location.name} · ${state.statusLine}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink700,
            )
        }

        SigapPanel(title = "Dihasilkan sistem", description = "Jam server dari timeline, tidak dapat diubah.") {
            SystemTimes(state.incident)
        }

        if (state.canWrite) {
            SigapPanel(title = "Diisi pengguna") {
                AnswerField(values.answer, answerError) { answer ->
                    values = values.copy(answer = answer)
                    answerError = null
                }
            }
            if (state.draftedFromLog) {
                NoticeStrip(
                    text = "Irama, tabel tindakan, waktu penghentian CPR, dan hasil diusulkan dari log tindakan " +
                        "resusitasi (ketukan tim). Periksa dan ubah bila perlu sebelum menyimpan.",
                    tone = colors.signalAmber,
                )
            }
            CodeBlueFormEditor(state.incident, values.form) { values = values.copy(form = it) }
            SigapPanel {
                UnknownableField("Tindakan yang dilakukan", values.actions) { values = values.copy(actions = it) }
                UnknownableField("Kendala selama respons", values.obstacles) { values = values.copy(obstacles = it) }
                SigapTextField(
                    label = "Catatan",
                    value = values.notes,
                    onValueChange = { values = values.copy(notes = it) },
                    hint = "Termasuk koreksi bila jam tombol tidak sesuai kejadian; jam sistem sendiri tidak diubah.",
                    singleLine = false,
                    minLines = 3,
                    maxLength = CodeBlueFormLimits.MAX_NOTES,
                )
            }
        } else {
            ReadOnlyDocumentation(state.incident, values)
        }

        SigapPanel(title = "Tidak diketahui") {
            if (values.unknownItems.isEmpty()) {
                Text("Tidak ada.", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
            } else {
                values.unknownItems.forEach { item ->
                    Text("•  $item", style = MaterialTheme.typography.bodyMedium, color = colors.ink950)
                }
            }
        }
    }
}

@Composable
private fun DocumentationBar(
    state: DocumentationUiState,
    answerError: String?,
    onSave: () -> Unit,
    onComplete: () -> Unit,
    onReload: () -> Unit,
    onOverwrite: () -> Unit,
) {
    val colors = SigapTheme.colors
    val busy = state.pending != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.paper0)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = colors.line200)
        Column(
            modifier = Modifier.padding(horizontal = Space.x4, vertical = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            if (state.versionConflict) {
                InlineError("Draft sudah diubah orang lain, dan ketikan Anda belum tersimpan. Pilih salah satu.")
                SigapButton("Ganti ketikan saya dengan versi terbaru", onReload, fullWidth = true, enabled = !busy)
                SigapButton("Timpa dengan ketikan saya", onOverwrite, variant = ButtonVariant.Ghost, fullWidth = true, enabled = !busy)
            } else {
                answerError?.let { InlineError(it) }
                state.error?.let { InlineError(it) }
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.ink700)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                SigapButton(
                    text = if (state.pending == DocumentationPending.SAVE) "Menyimpan…" else "Simpan draft",
                    onClick = onSave,
                    enabled = !busy,
                    size = ButtonSize.Large,
                )
                SigapButton(
                    text = if (state.pending == DocumentationPending.COMPLETE) "Menyelesaikan…" else "Selesaikan",
                    onClick = onComplete,
                    variant = ButtonVariant.Primary,
                    enabled = !busy,
                    size = ButtonSize.Large,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SystemTimes(incident: Incident) {
    SystemTimeRow("Incident dibuat", incident.createdAt)
    SystemTimeRow("Menuju lokasi", incident.acknowledgedAt)
    SystemTimeRow("Tiba di lokasi", incident.arrivedAt)
    SystemTimeRow("Respons dinyatakan selesai", incident.resolvedAt)
    DefinitionRow(
        label = "Menuju lokasi sampai tiba",
        value = formatDuration(incident.acknowledgedAt, incident.arrivedAt),
        dataValue = incident.acknowledgedAt != null && incident.arrivedAt != null,
    )
    DefinitionRow(
        label = "Tiba sampai selesai",
        value = formatDuration(incident.arrivedAt, incident.resolvedAt),
        dataValue = incident.arrivedAt != null && incident.resolvedAt != null,
    )
}

@Composable
private fun SystemTimeRow(label: String, at: Instant?) {
    DefinitionRow(label, at?.let(::formatTime) ?: "tidak tercatat", dataValue = at != null)
}

@Composable
private fun AnswerField(answer: ResponseAnswer?, error: String?, onAnswer: (ResponseAnswer) -> Unit) {
    val colors = SigapTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        Text(
            text = "Respons tim terhadap incident ini sudah selesai dijalankan? *",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink950,
        )
        RadioList(Answers, answer, onAnswer)
        error?.let { InlineError(it) }
    }
}

@Composable
private fun UnknownableField(label: String, value: TextAnswer, onChange: (TextAnswer) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        if (!value.unknown) {
            SigapTextField(
                label = label,
                value = value.text,
                onValueChange = { onChange(value.copy(text = it)) },
                singleLine = false,
                minLines = 3,
                maxLength = CodeBlueFormLimits.MAX_ANSWER,
            )
        }
        CheckRow(
            label = if (value.unknown) "$label: tidak diketahui" else "Tidak diketahui",
            checked = value.unknown,
            onChange = { onChange(value.copy(unknown = it)) },
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    SigapPanel(title = title) {
        content()
    }
}

@Composable
private fun CodeBlueFormEditor(incident: Incident, form: CodeBlueForm, onChange: (CodeBlueForm) -> Unit) {
    val colors = SigapTheme.colors
    Text(
        text = "Dokumentasi Tindakan Code Blue",
        style = MaterialTheme.typography.titleLarge,
        color = colors.ink950,
        modifier = Modifier.semantics { heading() },
    )

    FormSection("Waktu") {
        TimeField("Waktu mulai CPR", form.cprStartedAt) { onChange(form.copy(cprStartedAt = it)) }
        SystemTimeRow("Waktu panggilan tim (dari sistem)", incident.createdAt)
        SystemTimeRow("Waktu kedatangan tim (dari sistem)", incident.arrivedAt)
    }
    FormSection("Jenis kegagalan") {
        MultiChoiceChips(FailureTypes, form.failureTypes, { onChange(form.copy(failureTypes = it)) })
    }
    FormSection("Tempat kejadian") {
        SigapTextField(
            label = "Tempat kejadian",
            value = form.place,
            onValueChange = { onChange(form.copy(place = it)) },
            maxLength = CodeBlueFormLimits.MAX_TEXT,
        )
    }
    FormSection("Jalan napas") {
        MultiChoiceChips(Airway, form.airway, { onChange(form.copy(airway = it)) })
    }
    FormSection("Pernapasan") {
        MultiChoiceChips(Breathing, form.breathing, { onChange(form.copy(breathing = it)) })
    }
    FormSection("Irama jantung") {
        MultiChoiceChips(Rhythm, form.rhythm, { onChange(form.copy(rhythm = it)) })
    }
    FormSection("Diagnosa") {
        SigapTextField(
            label = "Diagnosa utama",
            value = form.primaryDiagnosis,
            onValueChange = { onChange(form.copy(primaryDiagnosis = it)) },
            maxLength = CodeBlueFormLimits.MAX_TEXT,
        )
        SigapTextField(
            label = "Diagnosa sekunder",
            value = form.secondaryDiagnosis,
            onValueChange = { onChange(form.copy(secondaryDiagnosis = it)) },
            maxLength = CodeBlueFormLimits.MAX_TEXT,
        )
    }
    FormSection("Obat / cairan intravena, EKG dan TTV") {
        if (form.rows.isEmpty()) {
            Text("Belum ada baris.", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
        }
        form.rows.forEachIndexed { index, row ->
            FormRowEditor(
                number = index + 1,
                row = row,
                onChange = { next -> onChange(form.copy(rows = form.rows.toMutableList().also { it[index] = next })) },
                onDelete = { onChange(form.copy(rows = form.rows.filterIndexed { i, _ -> i != index })) },
            )
        }
        if (form.rows.size < CodeBlueFormLimits.MAX_ROWS) {
            SigapButton(
                text = "Tambah baris",
                onClick = { onChange(form.copy(rows = form.rows + CodeBlueFormRow())) },
                size = ButtonSize.Small,
                icon = R.drawable.ic_plus,
            )
        }
    }
    FormSection("Penghentian CPR dan hasil") {
        TimeField("Waktu penghentian CPR", form.cprStoppedAt) { onChange(form.copy(cprStoppedAt = it)) }
        Column {
            Text("Hasil", style = MaterialTheme.typography.bodyMedium, color = colors.ink950)
            RadioList(Outcomes, form.outcome, { onChange(form.copy(outcome = it)) })
            if (form.outcome != null) {
                SigapButton("Kosongkan", { onChange(form.copy(outcome = null)) }, variant = ButtonVariant.Ghost, size = ButtonSize.Small)
            }
        }
        SigapTextField(
            label = "Dinyatakan meninggal oleh",
            value = form.declaredDeadBy,
            onValueChange = { onChange(form.copy(declaredDeadBy = it)) },
            singleLine = false,
            minLines = 2,
            maxLength = CodeBlueFormLimits.MAX_TEXT,
        )
        SigapTextField(
            label = "Alasan penghentian CPR",
            value = form.cprStopReason,
            onValueChange = { onChange(form.copy(cprStopReason = it)) },
            singleLine = false,
            minLines = 2,
            maxLength = CodeBlueFormLimits.MAX_TEXT,
        )
        SigapTextField(
            label = "Dokter verifikator",
            value = form.verifiedBy,
            onValueChange = { onChange(form.copy(verifiedBy = it)) },
            maxLength = CodeBlueFormLimits.MAX_TEXT,
        )
    }
}

@Composable
private fun FormRowEditor(
    number: Int,
    row: CodeBlueFormRow,
    onChange: (CodeBlueFormRow) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = SigapTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.paper50)
            .padding(Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Text("BARIS $number", style = SigapTheme.data.xs, color = colors.ink700)
        TimeField("Jam", row.time) { onChange(row.copy(time = it)) }
        Cell("Obat / cairan IV · Nama", row.drugName) { onChange(row.copy(drugName = it)) }
        CellPair("Dosis", row.dose, { onChange(row.copy(dose = it)) }, "Rute", row.route, { onChange(row.copy(route = it)) })
        CellPair("Joule defib", row.joule, { onChange(row.copy(joule = it)) }, "TD", row.bp, { onChange(row.copy(bp = it)) })
        CellPair("HR", row.hr, { onChange(row.copy(hr = it)) }, "RR", row.rr, { onChange(row.copy(rr = it)) })
        Cell("Irama", row.rhythm) { onChange(row.copy(rhythm = it)) }
        Cell("Catatan perkembangan", row.note) { onChange(row.copy(note = it)) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SigapButton("Hapus baris", onDelete, variant = ButtonVariant.Ghost, size = ButtonSize.Small, icon = R.drawable.ic_trash)
        }
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    SigapTextField(label, value, onChange, modifier = modifier, maxLength = CodeBlueFormLimits.MAX_CELL)
}

@Composable
private fun CellPair(
    leftLabel: String,
    leftValue: String,
    onLeft: (String) -> Unit,
    rightLabel: String,
    rightValue: String,
    onRight: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
        Cell(leftLabel, leftValue, Modifier.weight(1f), onLeft)
        Cell(rightLabel, rightValue, Modifier.weight(1f), onRight)
    }
}

@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = SigapTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink950)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = 112.dp)
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .background(colors.paper0)
                    .border(1.dp, colors.line500, shape)
                    .clickable(role = Role.Button) { picking = true }
                    .padding(horizontal = Space.x4)
                    .semantics { contentDescription = "$label: ${value.ifEmpty { "belum diisi" }}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value.ifEmpty { "--:--" },
                    style = SigapTheme.data.md,
                    color = if (value.isEmpty()) colors.ink700 else colors.ink950,
                )
            }
            SigapButton("Sekarang", { onChange(LocalTime.now().format(HourMinute)) }, size = ButtonSize.Small)
            if (value.isNotEmpty()) {
                SigapButton("Hapus", { onChange("") }, variant = ButtonVariant.Ghost, size = ButtonSize.Small)
            }
        }
    }
    if (picking) {
        TimePickerDialog(
            title = label,
            value = value,
            onDismiss = { picking = false },
            onConfirm = {
                onChange(it)
                picking = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(title: String, value: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val initial = value.takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it, HourMinute) } ?: LocalTime.now()
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SigapTheme.colors.paper0,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { TimePicker(state = picker) },
        confirmButton = {
            SigapButton(
                text = "Pakai jam ini",
                onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute).format(HourMinute)) },
                variant = ButtonVariant.Primary,
            )
        },
        dismissButton = { SigapButton("Batal", onDismiss) },
    )
}

@Composable
private fun ReadOnlyDocumentation(incident: Incident, values: DocumentationValues) {
    val form = values.form
    fun text(value: String) = value.ifBlank { "Tidak diisi" }
    fun labels(options: List<ChoiceOption<String>>, selected: Set<String>) =
        if (selected.isEmpty()) {
            "Tidak diisi"
        } else {
            selected.joinToString(", ") { value -> options.firstOrNull { it.value == value }?.label ?: value }
        }
    fun answer(field: TextAnswer) = if (field.unknown) "Tidak diketahui" else text(field.text)
    SigapPanel(title = "Dokumentasi Tindakan Code Blue") {
        SystemTimeRow("Waktu panggilan tim (dari sistem)", incident.createdAt)
        SystemTimeRow("Waktu kedatangan tim (dari sistem)", incident.arrivedAt)
        DefinitionRow("Waktu mulai CPR", text(form.cprStartedAt), dataValue = form.cprStartedAt.isNotBlank())
        DefinitionRow("Jenis kegagalan", labels(FailureTypes, form.failureTypes))
        DefinitionRow("Tempat kejadian", text(form.place))
        DefinitionRow("Jalan napas", labels(Airway, form.airway))
        DefinitionRow("Pernapasan", labels(Breathing, form.breathing))
        DefinitionRow("Irama jantung", labels(Rhythm, form.rhythm))
        DefinitionRow("Diagnosa utama", text(form.primaryDiagnosis))
        DefinitionRow("Diagnosa sekunder", text(form.secondaryDiagnosis))
        DefinitionRow("Waktu penghentian CPR", text(form.cprStoppedAt), dataValue = form.cprStoppedAt.isNotBlank())
        DefinitionRow("Hasil", form.outcome?.let { value -> Outcomes.first { it.value == value }.label } ?: "Tidak diisi")
        DefinitionRow("Dinyatakan meninggal oleh", text(form.declaredDeadBy))
        DefinitionRow("Alasan penghentian CPR", text(form.cprStopReason))
        DefinitionRow("Dokter verifikator", text(form.verifiedBy))
        HorizontalDivider(color = SigapTheme.colors.line200)
        DefinitionRow(
            "Respons selesai dijalankan",
            Answers.firstOrNull { it.value == values.answer }?.label ?: "Belum dijawab",
        )
        DefinitionRow("Tindakan yang dilakukan", answer(values.actions))
        DefinitionRow("Kendala selama respons", answer(values.obstacles))
        DefinitionRow("Catatan", values.notes.ifBlank { "Tidak ada catatan" })
    }
}
