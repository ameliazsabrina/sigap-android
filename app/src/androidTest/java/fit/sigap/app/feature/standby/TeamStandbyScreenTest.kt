package fit.sigap.app.feature.standby

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fit.sigap.app.core.model.ConnectionState
import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.feature.common.DutyTab
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TeamStandbyScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = object : TeamStandbyActions {
        override fun onOpenIncident(id: String) { calls += "incident:$id" }
        override fun onAcknowledge(id: String) { calls += "acknowledge:$id" }
        override fun onArrive(id: String) { calls += "arrive:$id" }
        override fun onResolve(id: String) { calls += "resolve:$id" }
        override fun onDocument(id: String) { calls += "document:$id" }
        override fun onOpenAlert(id: String) { calls += "alert:$id" }
        override fun onOpenReadiness() { calls += "readiness" }
        override fun onRetry() { calls += "retry" }
        override fun onSelectTab(tab: DutyTab) { calls += "tab:$tab" }
    }

    private fun show(
        incidents: List<Incident> = emptyList(),
        awaitingDocumentation: List<Incident> = emptyList(),
        alerts: List<EarlyAlert> = emptyList(),
        overdueIds: Set<String> = emptySet(),
        readiness: Readiness = PreviewData.readyPhone,
    ) {
        val state = TeamStandbyUiState(
            userName = PreviewData.RESPONDER,
            shift = PreviewData.shift,
            connection = ConnectionState.LIVE,
            readiness = readiness,
            incidents = incidents,
            awaitingDocumentation = awaitingDocumentation,
            alerts = alerts,
            overdueIds = overdueIds,
            phases = emptyMap(),
        )
        rule.setContent { PreviewTheme { TeamStandbyScreen(state, PreviewNow, actions) } }
    }

    @Test
    fun nothingActive_showsReadyCardAndOnlySiagaAndAkunTabs() {
        show()

        rule.onNodeWithText(PreviewData.RESPONDER).assertExists()
        rule.onNode(hasText("Siap menerima Code Blue", substring = true)).assertExists()
        rule.onNodeWithText("CODE BLUE").assertDoesNotExist()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(2)
        rule.onNodeWithText("Siaga").assertExists()
        rule.onNodeWithText("Akun").assertExists()
        rule.onNodeWithText("Riwayat").assertDoesNotExist()
    }

    @Test
    fun unacknowledgedIncident_takesOverAndAcknowledgeCallsWithItsId() {
        show(incidents = listOf(PreviewData.unacknowledgedIncident))

        rule.onNodeWithText("CODE BLUE").assertExists()
        rule.onNode(hasText("Siap menerima Code Blue", substring = true)).assertDoesNotExist()
        rule.onNodeWithText("Saya menuju lokasi").performScrollTo().performClick()

        assertEquals(listOf("acknowledge:${PreviewData.unacknowledgedIncident.id}"), calls)
    }

    @Test
    fun overdueIncident_isMarkedAndOffersArrival() {
        val overdue = PreviewData.acknowledgedByMe
        show(incidents = listOf(overdue), overdueIds = setOf(overdue.id))

        rule.onNodeWithText("BELUM TIBA").assertExists()
        rule.onNode(hasText("Batas waktu tiba sudah lewat", substring = true)).assertExists()
        rule.onNodeWithText("Tiba di lokasi").performScrollTo().performClick()

        assertEquals(listOf("arrive:${overdue.id}"), calls)
    }

    @Test
    fun notOverdue_acknowledgedIncidentIsNotMarked() {
        show(incidents = listOf(PreviewData.acknowledgedByMe))

        rule.onNodeWithText("BELUM TIBA").assertDoesNotExist()
        rule.onNode(hasText("Anda belum tiba", substring = true)).assertDoesNotExist()
    }

    @Test
    fun earlyAlerts_areInformationOnlyWithoutNurseActions() {
        show(alerts = listOf(PreviewData.uncheckedAlert))

        rule.onNodeWithText("Waspada · 1").assertExists()
        rule.onNode(hasText("Menunggu perawat", substring = true)).assertExists()
        listOf("Cek pasien", "Tidak kritis", "Panggil Code Blue").forEach { label ->
            rule.onNode(hasText(label) and hasClickAction()).assertDoesNotExist()
        }
        rule.onNode(hasText(PreviewData.uncheckedAlert.patient.locationName, substring = true))
            .performScrollTo()
            .performClick()

        assertEquals(listOf("alert:${PreviewData.uncheckedAlert.id}"), calls)
    }

    @Test
    fun awaitingDocumentation_opensThroughOnDocument() {
        show(awaitingDocumentation = listOf(PreviewData.awaitingDocumentation))

        rule.onNodeWithText("Menunggu dokumentasi · 1").assertExists()
        rule.onNode(hasText(PreviewData.awaitingDocumentation.number, substring = true))
            .performScrollTo()
            .performClick()

        assertEquals(listOf("document:${PreviewData.awaitingDocumentation.id}"), calls)
    }

    @Test
    fun blockedReadiness_showsCodeBlueBlocker() {
        show(readiness = PreviewData.pushMissing)

        rule.onNodeWithText("Push belum aktif", substring = true).assertExists()
        rule.onNodeWithText("Code Blue hanya terlihat", substring = true).assertExists()
        rule.onNodeWithText("Lihat kesiapan HP").performScrollTo().performClick()

        assertEquals(listOf("readiness"), calls)
    }
}
