package fit.sigap.app.feature.incident

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fit.sigap.app.core.model.Incident
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IncidentScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val responseLabels = listOf("Saya menuju lokasi", "Tiba di lokasi", "Nyatakan penanganan selesai", "Isi dokumentasi (wajib)")

    private fun show(incident: Incident, canRespond: Boolean, overdue: Boolean = false, phase: CommandPhase = CommandPhase.Idle) {
        rule.setContent {
            PreviewTheme {
                IncidentScreen(
                    incident = incident,
                    now = PreviewNow,
                    overdue = overdue,
                    canRespond = canRespond,
                    phase = phase,
                    onBack = { calls += "back" },
                    onAcknowledge = { calls += "acknowledge" },
                    onArrive = { calls += "arrive" },
                    onResolve = { calls += "resolve" },
                    onDocument = { calls += "document" },
                )
            }
        }
    }

    private fun assertOnlyAction(label: String, expectedCall: String) {
        responseLabels.filterNot { it == label }.forEach { other ->
            rule.onNode(hasText(other) and hasClickAction()).assertDoesNotExist()
        }
        rule.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf(expectedCall), calls)
    }

    @Test
    fun team_unacknowledged_offersOnlyAcknowledge() {
        show(PreviewData.unacknowledgedIncident, canRespond = true)
        assertOnlyAction("Saya menuju lokasi", "acknowledge")
    }

    @Test
    fun team_acknowledged_offersOnlyArrive() {
        show(PreviewData.acknowledgedByMe, canRespond = true)
        assertOnlyAction("Tiba di lokasi", "arrive")
    }

    @Test
    fun team_arrived_offersOnlyResolve() {
        show(PreviewData.arrived, canRespond = true)
        assertOnlyAction("Nyatakan penanganan selesai", "resolve")
    }

    @Test
    fun team_resolved_offersOnlyDocument() {
        show(PreviewData.awaitingDocumentation, canRespond = true)
        assertOnlyAction("Isi dokumentasi (wajib)", "document")
    }

    @Test
    fun nurse_seesNoResponseActions() {
        show(PreviewData.unacknowledgedIncident, canRespond = false)

        rule.onNode(hasText("Tombol respons tidak ditampilkan untuk peran Anda", substring = true)).assertExists()
        responseLabels.forEach { label ->
            rule.onNode(hasText(label) and hasClickAction()).assertDoesNotExist()
        }
    }

    @Test
    fun nurse_acknowledgedIncident_stillNoResponseActions() {
        show(PreviewData.acknowledgedByOther, canRespond = false)

        rule.onNodeWithText("Sudah ada yang menuju lokasi, belum tiba").assertExists()
        responseLabels.forEach { label ->
            rule.onNode(hasText(label) and hasClickAction()).assertDoesNotExist()
        }
    }

    @Test
    fun overdue_showsWarning() {
        show(PreviewData.acknowledgedByMe, canRespond = true, overdue = true)

        rule.onNodeWithText("Belum tiba").assertExists()
        rule.onNode(hasText("Batas waktu tiba sudah lewat", substring = true)).assertExists()
    }

    @Test
    fun notOverdue_hasNoWarning() {
        show(PreviewData.acknowledgedByMe, canRespond = true, overdue = false)

        rule.onNodeWithText("Belum tiba").assertDoesNotExist()
    }

    @Test
    fun sendingBlocksADoubleTap() {
        var phase by mutableStateOf<CommandPhase>(CommandPhase.Idle)
        rule.setContent {
            PreviewTheme {
                IncidentScreen(
                    incident = PreviewData.unacknowledgedIncident,
                    now = PreviewNow,
                    overdue = false,
                    canRespond = true,
                    phase = phase,
                    onBack = {},
                    onAcknowledge = {
                        calls += "acknowledge"
                        phase = CommandPhase.Sending
                    },
                    onArrive = {},
                    onResolve = {},
                    onDocument = {},
                )
            }
        }

        rule.onNodeWithText("Saya menuju lokasi").performScrollTo().performClick()
        rule.onNodeWithText("Memproses…").assertIsNotEnabled().performClick()

        assertEquals(listOf("acknowledge"), calls)
    }
}
