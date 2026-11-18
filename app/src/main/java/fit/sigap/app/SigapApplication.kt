package fit.sigap.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.installations.FirebaseInstallations
import fit.sigap.app.core.alarm.AlarmRegistry
import fit.sigap.app.core.data.DocumentationRepository
import fit.sigap.app.core.data.DutyRepository
import fit.sigap.app.core.device.ReadinessReader
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.push.AlertNotifier
import fit.sigap.app.core.push.FirebasePushTokenSource
import fit.sigap.app.core.push.PushRegistrar
import fit.sigap.app.core.push.PushRepository
import fit.sigap.app.core.push.SigapMessagingService
import fit.sigap.app.core.session.KeystoreTokenStore
import fit.sigap.app.core.session.SessionRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val session = SessionRepository(createAuthApi(BuildConfig.API_BASE_URL), KeystoreTokenStore(application))
    private val api = createSigapApi(BuildConfig.API_BASE_URL, session)
    val duty = DutyRepository(api)
    val documentation = DocumentationRepository(api)
    val readiness = ReadinessReader(application)
    val push = PushRepository(api, FirebasePushTokenSource)
    val alerts = AlertNotifier(application)
    val alarms = AlarmRegistry()
    val pushRegistrar = PushRegistrar(
        session,
        push,
        appScope,
        log = { if (BuildConfig.DEBUG) Log.d(SigapMessagingService.TAG, it) },
    )

    suspend fun signOut() {
        session.logout()
        alarms.settleAll()
        alerts.cancelAll()
        try {
            FirebasePushTokenSource.delete()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            Log.w(SigapMessagingService.TAG, "push token not deleted", cause)
        }
    }

    fun settleAlarms(ids: Set<String>) {
        alerts.cancelSettled(ids)
        alarms.settle(ids)
    }
}

class SigapApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.alerts.ensureChannels()
        container.pushRegistrar.start()
        if (BuildConfig.DEBUG) {
            FirebaseInstallations.getInstance().id
                .addOnSuccessListener { Log.d(SigapMessagingService.TAG, "fid=$it") }
                .addOnFailureListener { Log.w(SigapMessagingService.TAG, "fid unavailable", it) }
        }
    }
}

inline fun <reified VM : ViewModel> factoryOf(crossinline create: () -> VM) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
