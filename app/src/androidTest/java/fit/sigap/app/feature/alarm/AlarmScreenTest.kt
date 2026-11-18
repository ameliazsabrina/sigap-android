package fit.sigap.app.feature.alarm

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.minutesAgo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlarmScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val codeBlue = AlarmUiState(
        kind = AlarmKind.CODE_BLUE,
        headline = "Code Blue",
        location = "Rawat Inap Melati",
        detail = "Gedung A · Lantai 3 · Ruang 301 · Bed MLT-301A",
        createdAt = minutesAgo(1),
        waitingLine = "Belum ada yang menuju lokasi",
        primaryLabel = "Saya menuju lokasi",
        busyLabel = "Memproses…",
    )

    private val earlyAlert = AlarmUiState(
        kind = AlarmKind.EARLY_ALERT,
        headline = "Early alert kritis",
        location = "MLT-301A",
        detail = "Ny. Siti Aminah · Single Red · Laju napas 8 /menit",
        createdAt = minutesAgo(1),
        waitingLine = "Menunggu pengecekan",
        primaryLabel = "Cek pasien",
        busyLabel = "Mencatat…",
    )

    private fun show(state: AlarmUiState) {
        rule.setContent {
            PreviewTheme {
                AlarmScreen(state, PreviewNow, onPrimary = { calls += "primary" }, onDetail = { calls += "detail" })
            }
        }
    }

    @Test
    fun codeBlue_primaryCallsOnPrimary() {
        show(codeBlue)

        rule.onNode(hasText("Rawat Inap Melati", substring = true)).assertExists()
        rule.onNode(hasText("Belum ada yang menuju lokasi", substring = true)).assertExists()
        rule.onNodeWithText("Saya menuju lokasi").performScrollTo().performClick()

        assertEquals(listOf("primary"), calls)
    }

    @Test
    fun earlyAlert_primaryCallsOnPrimary() {
        show(earlyAlert)

        rule.onNode(hasText("Early alert kritis", substring = true)).assertExists()
        rule.onNodeWithText("Saya menuju lokasi").assertDoesNotExist()
        rule.onNodeWithText("Cek pasien").performScrollTo().performClick()

        assertEquals(listOf("primary"), calls)
    }

    @Test
    fun detailCallsOnDetail() {
        show(codeBlue)

        rule.onNodeWithText("Lihat detail").performScrollTo().performClick()

        assertEquals(listOf("detail"), calls)
    }

    @Test
    fun stoppedLine_showsStoppedStateWithoutPrimaryAction() {
        show(codeBlue.copy(stoppedLine = "Alarm berhenti: sudah ada yang menuju lokasi"))

        rule.onNode(hasText("Alarm berhenti: sudah ada yang menuju lokasi", substring = true)).assertExists()
        rule.onNode(hasText("Belum ada yang menuju lokasi", substring = true)).assertDoesNotExist()
        rule.onNode(hasText("Saya menuju lokasi") and hasClickAction()).assertDoesNotExist()
        rule.onNodeWithText("Lihat detail").assertExists()
    }

    @Test
    fun sending_disablesThePrimaryAction() {
        show(codeBlue.copy(phase = CommandPhase.Sending))

        rule.onNodeWithText("Memproses…").assertIsNotEnabled().performClick()
        rule.onNode(hasText("Alarm berhenti setelah server mencatatnya", substring = true)).assertExists()

        assertEquals(emptyList<String>(), calls)
    }
}
