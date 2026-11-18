package fit.sigap.app.feature.standby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fit.sigap.app.core.model.ConnectionState
import fit.sigap.app.core.model.firstWaiting
import fit.sigap.app.core.model.openSorted
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.feature.common.DutyTab
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NurseStandbyScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = object : NurseStandbyActions {
        override fun onCheckFirst(id: String) { calls += "check:$id" }
        override fun onOpenAlert(id: String) { calls += "alert:$id" }
        override fun onOpenIncident(id: String) { calls += "incident:$id" }
        override fun onOpenReadiness() { calls += "readiness" }
        override fun onRetry() { calls += "retry" }
        override fun onSelectTab(tab: DutyTab) { calls += "tab:$tab" }
    }

    private fun state(
        alerts: List<fit.sigap.app.core.model.EarlyAlert> = emptyList(),
        incidents: List<fit.sigap.app.core.model.Incident> = emptyList(),
        connection: ConnectionState = ConnectionState.LIVE,
        readiness: fit.sigap.app.core.model.Readiness = PreviewData.readyPhone,
        checkPhase: CommandPhase = CommandPhase.Idle,
        loading: Boolean = false,
        error: String? = null,
    ) = NurseStandbyUiState(
        userName = PreviewData.NURSE,
        shift = PreviewData.shift,
        connection = connection,
        readiness = readiness,
        alerts = alerts,
        incidents = incidents,
        patientCount = 12,
        monitorDisconnected = 0,
        checkPhase = checkPhase,
        loading = loading,
        error = error,
    )

    private fun show(state: NurseStandbyUiState) {
        rule.setContent { PreviewTheme { NurseStandbyScreen(state, PreviewNow, actions) } }
    }

    @Test
    fun header_showsNurseNameWardAndOnlySiagaAndAkunTabs() {
        show(state())

        rule.onNodeWithText(PreviewData.NURSE).assertExists()
        rule.onNode(hasText(PreviewData.shift.wardName, substring = true)).assertExists()
        rule.onNodeWithText("Siaga").assertExists()
        rule.onNodeWithText("Akun").performClick()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(2)
        rule.onNodeWithText("Riwayat").assertDoesNotExist()
        assertEquals(listOf("tab:ACCOUNT"), calls)
    }

    @Test
    fun waitingAlerts_bannerChecksTheFirstWaitingAlert() {
        val alerts = listOf(PreviewData.secondUncheckedAlert, PreviewData.checkedAlert, PreviewData.uncheckedAlert).openSorted()
        show(state(alerts = alerts))

        rule.onNodeWithText("2 PASIEN MENUNGGU").assertExists()
        rule.onNodeWithText("Cek pasien").performClick()

        assertEquals(listOf("check:${PreviewData.uncheckedAlert.id}"), calls)
        assertEquals(PreviewData.uncheckedAlert.id, alerts.firstWaiting()?.id)
    }

    @Test
    fun checkBanner_sendingBlocksASecondTap() {
        var phase by mutableStateOf<CommandPhase>(CommandPhase.Idle)
        val alerts = listOf(PreviewData.uncheckedAlert)
        rule.setContent {
            PreviewTheme {
                NurseStandbyScreen(
                    state(alerts = alerts, checkPhase = phase),
                    PreviewNow,
                    object : NurseStandbyActions by actions {
                        override fun onCheckFirst(id: String) {
                            calls += "check:$id"
                            phase = CommandPhase.Sending
                        }
                    },
                )
            }
        }

        rule.onNodeWithText("Cek pasien").performClick()
        rule.onNodeWithText("Mencatat…").assertIsNotEnabled().performClick()

        assertEquals(listOf("check:${PreviewData.uncheckedAlert.id}"), calls)
    }

    @Test
    fun checkBanner_failedShowsTheServerMessage() {
        show(state(alerts = listOf(PreviewData.uncheckedAlert), checkPhase = CommandPhase.Failed("Server tidak menjawab.")))

        rule.onNodeWithText("Server tidak menjawab.").assertExists()
        rule.onNodeWithText("Cek pasien").assertExists()
    }

    @Test
    fun blockedReadiness_showsAlertPasienBlockerThatOpensReadiness() {
        show(state(readiness = PreviewData.pushMissing))

        rule.onNodeWithText("Push belum aktif", substring = true).assertExists()
        rule.onNodeWithText("Alert pasien hanya terlihat", substring = true).assertExists()
        rule.onNodeWithText("Lihat kesiapan HP").performScrollTo().performClick()

        assertEquals(listOf("readiness"), calls)
    }

    @Test
    fun activeCodeBlueInWard_isVisibleAndOpensTheIncident() {
        show(state(incidents = listOf(PreviewData.unacknowledgedIncident)))

        rule.onNode(hasText("Code Blue · Rawat Inap Melati", substring = true) and hasClickAction())
            .performScrollTo()
            .performClick()

        assertEquals(listOf("incident:${PreviewData.unacknowledgedIncident.id}"), calls)
    }

    @Test
    fun loading_showsAlertCheckInProgress() {
        show(state(loading = true))

        rule.onNodeWithText("Memeriksa alert").assertExists()
        rule.onNodeWithText("Tidak ada alert aktif").assertDoesNotExist()
    }

    @Test
    fun error_retryCallsOnRetry() {
        show(state(error = "Server tidak dapat dihubungi."))

        rule.onNodeWithText("Server tidak dapat dihubungi.").assertExists()
        rule.onNodeWithText("Coba lagi").performScrollTo().performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun disconnected_isShownSoStaleDataDoesNotLookLive() {
        show(state(alerts = listOf(PreviewData.checkedAlert), connection = ConnectionState.POLLING))

        rule.onNodeWithText("Terputus").assertExists()
        rule.onNodeWithText("Terhubung").assertDoesNotExist()
        rule.onNodeWithText("Koneksi terputus · layar diperbarui tiap 10 detik").assertExists()
    }
}
