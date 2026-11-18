package fit.sigap.app.feature.alert

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
import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlertScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = object : AlertActions {
        override fun onBack() { calls += "back" }
        override fun onCheck() { calls += "check" }
        override fun onClose(reason: String) { calls += "close:$reason" }
        override fun onEscalate(reason: String) { calls += "escalate:$reason" }
    }

    private fun show(alert: EarlyAlert, canDecide: Boolean, phase: CommandPhase = CommandPhase.Idle) {
        rule.setContent {
            PreviewTheme {
                AlertScreen(AlertUiState(alert, null, PreviewData.vitals, canDecide, phase), PreviewNow, actions)
            }
        }
    }

    @Test
    fun nurse_uncheckedAlert_checkCallsOnCheck() {
        show(PreviewData.uncheckedAlert, canDecide = true)

        rule.onNodeWithText("Cek pasien").performScrollTo().performClick()

        assertEquals(listOf("check"), calls)
    }

    @Test
    fun nurse_checkedAlert_closeSendsTheDefaultReason() {
        show(PreviewData.checkedAlert, canDecide = true)

        rule.onNodeWithText("Tidak kritis").performScrollTo().performClick()
        rule.onNodeWithText("Nyatakan tidak kritis?").assertExists()
        rule.onNodeWithText("Tutup alert").performClick()

        assertEquals(listOf("close:Pasien sudah diperiksa dan dinilai tidak kritis."), calls)
    }

    @Test
    fun nurse_checkedAlert_escalateSendsTheDefaultReason() {
        show(PreviewData.checkedAlert, canDecide = true)

        rule.onNodeWithText("Panggil Code Blue").performScrollTo().performClick()
        rule.onNodeWithText("Panggil Code Blue?").assertExists()
        rule.onNodeWithText("Panggil sekarang").performClick()

        assertEquals(listOf("escalate:Perawat memanggil Code Blue setelah memeriksa pasien."), calls)
    }

    @Test
    fun nurse_closedAlert_showsClosedLineAndNoDecisionButtons() {
        show(PreviewData.closedAlert, canDecide = true)

        rule.onNodeWithText("Alert ditutup: pasien dinilai tidak kritis.").assertExists()
        assertNoDecisionControls()
    }

    @Test
    fun nurse_sendingBlocksADoubleTapOnCheck() {
        var phase by mutableStateOf<CommandPhase>(CommandPhase.Idle)
        rule.setContent {
            PreviewTheme {
                AlertScreen(
                    AlertUiState(PreviewData.uncheckedAlert, null, PreviewData.vitals, canDecide = true, phase = phase),
                    PreviewNow,
                    object : AlertActions by actions {
                        override fun onCheck() {
                            calls += "check"
                            phase = CommandPhase.Sending
                        }
                    },
                )
            }
        }

        rule.onNodeWithText("Cek pasien").performScrollTo().performClick()
        rule.onNodeWithText("Mencatat…").assertIsNotEnabled().performClick()

        assertEquals(listOf("check"), calls)
    }

    @Test
    fun nurse_sendingCloseBlocksASecondConfirm() {
        var phase by mutableStateOf<CommandPhase>(CommandPhase.Idle)
        rule.setContent {
            PreviewTheme {
                AlertScreen(
                    AlertUiState(PreviewData.checkedAlert, null, PreviewData.vitals, canDecide = true, phase = phase),
                    PreviewNow,
                    object : AlertActions by actions {
                        override fun onClose(reason: String) {
                            calls += "close"
                            phase = CommandPhase.Sending
                        }
                    },
                )
            }
        }

        rule.onNodeWithText("Tidak kritis").performScrollTo().performClick()
        rule.onNodeWithText("Tutup alert").performClick()
        rule.onNodeWithText("Mengirim…").assertIsNotEnabled().performClick()

        assertEquals(listOf("close"), calls)
    }

    @Test
    fun team_uncheckedAlert_hasNoNurseControls() {
        show(PreviewData.uncheckedAlert, canDecide = false)

        rule.onNodeWithText(PreviewData.uncheckedAlert.patient.locationName).assertExists()
        assertNoDecisionControls()
    }

    @Test
    fun team_checkedAlert_hasNoNurseControls() {
        show(PreviewData.checkedAlert, canDecide = false)

        rule.onNodeWithText(PreviewData.checkedAlert.patient.locationName).assertExists()
        assertNoDecisionControls()
    }

    private fun assertNoDecisionControls() {
        listOf("Cek pasien", "Tidak kritis", "Panggil Code Blue").forEach { label ->
            rule.onNode(hasText(label) and hasClickAction()).assertDoesNotExist()
        }
    }
}
