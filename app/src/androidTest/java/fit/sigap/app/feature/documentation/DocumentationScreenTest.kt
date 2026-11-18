package fit.sigap.app.feature.documentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.model.ResponseAnswer
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DocumentationScreenTest {
    @get:Rule val rule = createComposeRule()

    private val saved = mutableListOf<Pair<String, DocumentationValues?>>()

    private val actions = object : DocumentationActions {
        override fun onBack() { saved += "back" to null }
        override fun onSave(values: DocumentationValues) { saved += "save" to values }
        override fun onComplete(values: DocumentationValues) { saved += "complete" to values }
        override fun onReloadAfterConflict() { saved += "reload" to null }
        override fun onOverwriteAfterConflict(values: DocumentationValues) { saved += "overwrite" to values }
    }

    private fun show(canWrite: Boolean = true, versionConflict: Boolean = false, initial: DocumentationValues = DocumentationValues()) {
        val state = DocumentationUiState(
            incident = PreviewData.awaitingDocumentation,
            statusLine = if (canWrite) "Draft" else "Dokumentasi selesai",
            canWrite = canWrite,
            versionConflict = versionConflict,
        )
        rule.setContent { PreviewTheme { DocumentationScreen(state, initial, actions) } }
    }

    private fun enterAnswerAndNotes() {
        rule.onNodeWithText("Ya, sudah selesai").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Catatan").performScrollTo().performTextInput("Uji dokumentasi simulasi")
    }

    @Test
    fun saveDraft_passesEnteredValues() {
        show()
        enterAnswerAndNotes()

        rule.onNodeWithText("Simpan draft").performClick()

        val (action, values) = saved.single()
        assertEquals("save", action)
        assertEquals(ResponseAnswer.YES, values?.answer)
        assertEquals("Uji dokumentasi simulasi", values?.notes)
    }

    @Test
    fun complete_passesEnteredValues() {
        show()
        enterAnswerAndNotes()

        rule.onNodeWithText("Selesaikan").performClick()

        val (action, values) = saved.single()
        assertEquals("complete", action)
        assertEquals(ResponseAnswer.YES, values?.answer)
        assertEquals("Uji dokumentasi simulasi", values?.notes)
    }

    @Test
    fun complete_withoutAnswerIsBlockedWithAnError() {
        show()

        rule.onNodeWithText("Selesaikan").performClick()

        assertTrue(saved.isEmpty())
        rule.onAllNodes(hasText("Wajib dijawab", substring = true)).assertCountEquals(2)
    }

    @Test
    fun versionConflict_offersReloadAndOverwrite() {
        val initial = DocumentationValues(answer = ResponseAnswer.NO, notes = "Ketikan lokal")
        show(versionConflict = true, initial = initial)

        rule.onNode(hasText("Draft sudah diubah orang lain", substring = true)).assertExists()
        rule.onNodeWithText("Ganti ketikan saya dengan versi terbaru").performClick()
        rule.onNodeWithText("Timpa dengan ketikan saya").performClick()

        assertEquals(listOf("reload", "overwrite"), saved.map { it.first })
        assertEquals(initial, saved.last().second)
    }

    @Test
    fun readOnly_hasNoEditingControls() {
        show(canWrite = false, initial = DocumentationValues(answer = ResponseAnswer.YES, notes = "Catatan tersimpan"))

        rule.onNodeWithText("Simpan draft").assertDoesNotExist()
        rule.onNodeWithText("Selesaikan").assertDoesNotExist()
        rule.onNodeWithContentDescription("Catatan").assertDoesNotExist()
        rule.onNode(hasText("Catatan tersimpan", substring = true)).performScrollTo().assertExists()
    }
}
