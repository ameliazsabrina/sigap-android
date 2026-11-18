package fit.sigap.app.feature.duty

import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StartDutyScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    @Before
    fun grantCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
    }

    private fun show(phase: CommandPhase = CommandPhase.Idle, signOutPhase: CommandPhase = CommandPhase.Idle) {
        rule.setContent { PreviewTheme { StartDuty(phase, signOutPhase) } }
    }

    @androidx.compose.runtime.Composable
    private fun StartDuty(phase: CommandPhase, signOutPhase: CommandPhase) {
        StartDutyScreen(
            userName = PreviewData.NURSE,
            role = DutyRole.NURSE,
            phase = phase,
            onSubmitCode = { calls += "code:$it" },
            onClearError = { calls += "clear" },
            signOutPhase = signOutPhase,
            onSignOut = { calls += "signOut" },
            onDismissSignOut = { calls += "dismissSignOut" },
        )
    }

    @Test
    fun sixDigitCode_callsOnSubmitCodeOnce() {
        var phase by mutableStateOf<CommandPhase>(CommandPhase.Idle)
        rule.setContent {
            PreviewTheme {
                StartDutyScreen(
                    userName = PreviewData.NURSE,
                    role = DutyRole.NURSE,
                    phase = phase,
                    onSubmitCode = {
                        calls += "code:$it"
                        phase = CommandPhase.Sending
                    },
                    onClearError = {},
                    signOutPhase = CommandPhase.Idle,
                    onSignOut = {},
                    onDismissSignOut = {},
                )
            }
        }

        rule.onNodeWithText("Pindai QR absen").performClick()
        rule.onNodeWithText("Absen").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Atau ketik kode 6 angka").performScrollTo().performTextInput("482913")
        rule.onNodeWithText("Absen").performClick()
        rule.onNodeWithText("Absen").assertIsNotEnabled().performClick()

        assertEquals(listOf("code:482913"), calls)
    }

    @Test
    fun failedPhase_showsErrorOnStartScreen() {
        show(phase = CommandPhase.Failed("Kode absen salah atau sudah kedaluwarsa."))

        rule.onNodeWithText("Kode absen salah atau sudah kedaluwarsa.").assertExists()
    }

    @Test
    fun failedPhase_inScannerPanelRetryClearsTheError() {
        rule.setContent {
            PreviewTheme {
                ScannerPanel(
                    phase = CommandPhase.Failed("Kode absen salah atau sudah kedaluwarsa."),
                    onSubmitCode = { calls += "code:$it" },
                    onClearError = { calls += "clear" },
                    onCancel = { calls += "cancel" },
                )
            }
        }

        rule.onNodeWithText("Kode absen salah atau sudah kedaluwarsa.").assertExists()
        rule.onNodeWithText("Pindai lagi").performClick()

        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun signOut_goesThroughConfirmation() {
        show()

        rule.onNodeWithText("Keluar dari HP ini").performScrollTo().performClick()
        assertEquals(emptyList<String>(), calls)
        rule.onNodeWithText("Keluar dari HP ini?").assertExists()
        rule.onNodeWithText("Keluar").performClick()

        assertEquals(listOf("signOut"), calls)
    }

    @Test
    fun signOut_cancelDismissesWithoutSigningOut() {
        show()

        rule.onNodeWithText("Keluar dari HP ini").performScrollTo().performClick()
        rule.onNodeWithText("Batal").performClick()

        assertEquals(listOf("dismissSignOut"), calls)
        rule.onNodeWithText("Keluar dari HP ini?").assertDoesNotExist()
    }
}
