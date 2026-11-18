package fit.sigap.app.feature.account

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(uncheckedAlerts: Int, role: DutyRole = DutyRole.NURSE) {
        rule.setContent {
            PreviewTheme {
                AccountScreen(
                    userName = PreviewData.NURSE,
                    role = role,
                    shift = PreviewData.shift,
                    uncheckedAlerts = uncheckedAlerts,
                    phase = CommandPhase.Idle,
                    onFinish = { calls += "finish" },
                    onOpenReadiness = { calls += "readiness" },
                    onSelectTab = { calls += "tab:$it" },
                )
            }
        }
    }

    @Test
    fun nurse_uncheckedAlertsAreSurfacedBeforeFinishing() {
        show(uncheckedAlerts = 2)

        rule.onNodeWithText("Selesai bertugas").performScrollTo().performClick()

        assertEquals(emptyList<String>(), calls)
        rule.onNodeWithText("Masih ada pasien belum dicek").assertExists()
        rule.onNode(hasText("2 alert di ${PreviewData.shift.wardName} belum dicek", substring = true)).assertExists()
        rule.onNodeWithText("Tetap selesai").performClick()

        assertEquals(listOf("finish"), calls)
    }

    @Test
    fun nurse_cancelKeepsDuty() {
        show(uncheckedAlerts = 1)

        rule.onNodeWithText("Selesai bertugas").performScrollTo().performClick()
        rule.onNodeWithText("Batal").performClick()

        assertEquals(emptyList<String>(), calls)
        rule.onNodeWithText("Masih ada pasien belum dicek").assertDoesNotExist()
    }

    @Test
    fun noUncheckedAlerts_finishesWithoutConfirmation() {
        show(uncheckedAlerts = 0)

        rule.onNodeWithText("Selesai bertugas").performScrollTo().performClick()

        assertEquals(listOf("finish"), calls)
        rule.onNodeWithText("Masih ada pasien belum dicek").assertDoesNotExist()
    }

    @Test
    fun showsRoleAndOpensReadiness() {
        show(uncheckedAlerts = 0)

        rule.onNode(hasText("Perawat · HP terpasang", substring = true)).assertExists()
        rule.onNodeWithText("Kesiapan HP", substring = true).performClick()

        assertEquals(listOf("readiness"), calls)
    }
}
