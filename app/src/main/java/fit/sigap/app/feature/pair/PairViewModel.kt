package fit.sigap.app.feature.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PairViewModel(
    private val session: SessionRepository,
    private val pairOrigin: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PairUiState(PairStep.SCAN))
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { current ->
                if (current is SessionState.None && !_state.value.inSetup) {
                    _state.value = PairUiState(if (current.ended) PairStep.SESSION_ENDED else PairStep.SCAN)
                }
            }
        }
    }

    fun startScan() = _state.update { it.copy(scanning = true, error = null, retryToken = null) }

    fun cancelScan() = _state.update { it.copy(scanning = false) }

    fun onScanned(raw: String) {
        if (_state.value.busy) return
        when (val scanned = parsePairingQr(raw, pairOrigin)) {
            is ScannedPairing.Token -> pair(scanned.value)
            ScannedPairing.CheckInCode -> fail(
                "Ini QR absen dari nurse station. Untuk memasangkan HP, minta QR pairing pribadi Anda dari Administrator.",
            )
            ScannedPairing.NotSigap -> fail("QR ini bukan QR pairing SIGAP. Minta QR pairing pribadi Anda dari Administrator.")
        }
    }

    fun retry() {
        _state.value.retryToken?.let(::pair)
    }

    fun onNotificationsResult(granted: Boolean) = _state.update {
        if (granted) it.copy(step = PairStep.BATTERY, notificationsDenied = false) else it.copy(notificationsDenied = true)
    }

    fun skipNotifications() = _state.update { it.copy(step = PairStep.BATTERY, notificationsDenied = false) }

    fun finishSetup() = _state.update { it.copy(inSetup = false) }

    private fun fail(message: String) = _state.update { it.copy(scanning = false, error = message, retryToken = null) }

    private fun pair(token: String) {
        _state.update { it.copy(busy = true, scanning = false, error = null, inSetup = true) }
        viewModelScope.launch {
            try {
                val user = session.pair(token)
                _state.update {
                    it.copy(
                        step = PairStep.NOTIFY,
                        busy = false,
                        userName = user.name,
                        roleLabel = user.role.label,
                        retryToken = null,
                    )
                }
            } catch (cause: ApiException) {
                _state.update {
                    it.copy(
                        busy = false,
                        inSetup = false,
                        error = cause.message,
                        retryToken = token.takeIf { cause.isNetwork },
                    )
                }
            }
        }
    }
}
