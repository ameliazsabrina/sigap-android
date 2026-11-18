package fit.sigap.app.feature.documentation

import androidx.compose.runtime.Composable
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.model.ResponseAnswer
import fit.sigap.app.core.model.TextAnswer
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

private object NoDocumentationActions : DocumentationActions {
    override fun onBack() = Unit
    override fun onSave(values: DocumentationValues) = Unit
    override fun onComplete(values: DocumentationValues) = Unit
    override fun onReloadAfterConflict() = Unit
    override fun onOverwriteAfterConflict(values: DocumentationValues) = Unit
}

private val empty = DocumentationUiState(
    incident = PreviewData.awaitingDocumentation,
    statusLine = "Belum dimulai",
    canWrite = true,
)

private val draft = DocumentationValues(
    answer = ResponseAnswer.YES,
    actions = TextAnswer("Tim tiba dan melanjutkan penanganan sesuai prosedur ruangan."),
    obstacles = TextAnswer(unknown = true),
)

@Composable
private fun Render(state: DocumentationUiState, values: DocumentationValues = DocumentationValues()) = PreviewTheme {
    DocumentationScreen(state = state, initialValues = values, actions = NoDocumentationActions)
}

@SigapPreviews
@Composable
private fun DocumentationEmpty() = Render(empty)

@SigapPreviews
@Composable
private fun DocumentationDraft() = Render(
    empty.copy(statusLine = "Draft", message = "Draft tersimpan. Incident belum dinyatakan selesai."),
    draft,
)

@SigapPreviews
@Composable
private fun DocumentationConflict() = Render(empty.copy(statusLine = "Draft", versionConflict = true), draft)

@SigapPreviews
@Composable
private fun DocumentationReadOnly() = Render(
    empty.copy(incident = PreviewData.completed, statusLine = "Dokumentasi selesai", canWrite = false),
    draft,
)
