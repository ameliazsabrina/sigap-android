package fit.sigap.app.core.data

import fit.sigap.app.core.network.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.math.min

private const val FIRST_RETRY_MS = 1_000L
private const val MAX_RETRY_MS = 30_000L

class ResusOutbox(
    private val repository: ResusLogRepository,
    private val signedInUserId: Flow<String?>,
    private val scope: CoroutineScope,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            launch { runCatching { repository.pruneOld() } }
            signedInUserId
                .distinctUntilChanged()
                .flatMapLatest { userId -> if (userId == null) emptyFlow() else repository.pending(userId) }
                .collectLatest { queue ->
                    if (queue.isEmpty()) {
                        _lastError.value = null
                        return@collectLatest
                    }
                    var wait = FIRST_RETRY_MS
                    while (true) {
                        try {
                            queue.groupBy { it.incidentId }.forEach { (incidentId, batch) -> repository.send(incidentId, batch) }
                            _lastError.value = null
                            return@collectLatest
                        } catch (cause: ApiException) {
                            _lastError.value = cause.message
                        }
                        pause(wait)
                        wait = min(wait * 2, MAX_RETRY_MS)
                    }
                }
        }
    }
}
