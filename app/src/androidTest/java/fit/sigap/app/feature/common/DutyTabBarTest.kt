package fit.sigap.app.feature.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fit.sigap.app.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DutyTabBarTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun tappingAkun_selectsAccountTab() {
        var selected: DutyTab? = null
        rule.setContent { PreviewTheme { DutyTabBar(DutyTab.STANDBY, onSelect = { selected = it }) } }

        rule.onNodeWithText("Siaga").assertExists()
        rule.onNodeWithText("Akun").performClick()

        assertEquals(DutyTab.ACCOUNT, selected)
    }
}
