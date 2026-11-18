package fit.sigap.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fit.sigap.app.core.designsystem.theme.SigapAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x1B, 0x1B, 0x1B))
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)
        val container = (application as SigapApplication).container
        val debugPairingQr = intent.dataString.takeIf { BuildConfig.DEBUG && savedInstanceState == null }
        setContent {
            SigapAppTheme {
                AppRoot(container, debugPairingQr)
            }
        }
    }
}
