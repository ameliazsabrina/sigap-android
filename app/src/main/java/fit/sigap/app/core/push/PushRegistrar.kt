package fit.sigap.app.core.push

import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.core.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PushRegistrar(
    private val session: SessionRepository,
    private val push: PushRepository,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    private val retryDelaysMs: List<Long> = listOf(5_000, 15_000, 60_000),
) {
    private val lock = Mutex()

    fun start() {
        scope.launch {
            session.state
                .map { (it as? SessionState.Active)?.user?.id }
                .distinctUntilChanged()
                .collectLatest { userId -> if (userId != null) registerWithRetry() }
        }
    }

    fun onNewToken() {
        scope.launch {
            if (session.hasStoredSession()) registerWithRetry()
        }
    }

    suspend fun register(): PushStatus = lock.withLock { push.register() }

    private suspend fun registerWithRetry() {
        for (attempt in 0..retryDelaysMs.size) {
            try {
                val status = register()
                log("push registration ok (confirmed=${status.confirmed})")
                return
            } catch (cause: ApiException) {
                log("push registration failed: ${cause.code}")
                val retryable = cause.isNetwork || cause.code == PushRepository.CODE_TOKEN_UNAVAILABLE
                if (!retryable || attempt == retryDelaysMs.size) return
                delay(retryDelaysMs[attempt])
            }
        }
    }
}
