package fit.sigap.app.feature.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fit.sigap.app.MainActivity
import fit.sigap.app.SigapApplication
import fit.sigap.app.core.designsystem.theme.SigapAppTheme
import fit.sigap.app.core.session.SessionState
import fit.sigap.app.factoryOf
import fit.sigap.app.feature.common.rememberNow

class AlarmActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels {
        val container = (application as SigapApplication).container
        factoryOf {
            AlarmViewModel(
                registry = container.alarms,
                repository = container.duty,
                userId = { (container.session.state.value as? SessionState.Active)?.user?.id.orEmpty() },
                onSettled = container.alerts::cancel,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        setContent {
            SigapAppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val now by rememberNow()
                val current = state
                if (current == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    AlarmScreen(current, now, onPrimary = viewModel::primary, onDetail = ::openApp)
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun openApp() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val open = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        if (!keyguard.isKeyguardLocked) {
            open()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()
            },
        )
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
