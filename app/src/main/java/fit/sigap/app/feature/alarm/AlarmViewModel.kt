package fit.sigap.app.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fit.sigap.app.core.alarm.ActiveAlarm
import fit.sigap.app.core.alarm.AlarmRegistry
import fit.sigap.app.core.data.DutyRepository
import fit.sigap.app.core.model.IncidentStatus
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.push.AlarmTarget
import fit.sigap.app.feature.common.CommandPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private data class AlarmDetails(val location: String, val detail: String, val createdAt: Instant)

private data class Shown(
    val alarm: ActiveAlarm,
    val details: AlarmDetails? = null,
    val phase: CommandPhase = CommandPhase.Idle,
    val stoppedLine: String? = null,
)

class AlarmViewModel(
    private val registry: AlarmRegistry,
    private val repository: DutyRepository,
    private val userId: () -> String,
    private val onSettled: (id: String) -> Unit,
) : ViewModel() {
    private val shown = MutableStateFlow(registry.latest?.let(::Shown))
    private val _state = MutableStateFlow(shown.value?.toUiState())
    val state: StateFlow<AlarmUiState?> = _state.asStateFlow()

    private val intentKeys = mutableMapOf<String, String>()
    private var loading: Job? = null

    init {
        shown.value?.let { load(it.alarm.target) }
        viewModelScope.launch { registry.alarms.collect(::onAlarmsChanged) }
        viewModelScope.launch { shown.collect { _state.value = it?.toUiState() } }
    }

    fun primary() {
        val current = shown.value ?: return
        if (current.phase.isBusy || current.stoppedLine != null) return
        val target = current.alarm.target
        val key = intentKeys.getOrPut(target.id) { UUID.randomUUID().toString() }
        setPhase(CommandPhase.Sending)
        viewModelScope.launch {
            try {
                val stoppedLine = when (target) {
                    is AlarmTarget.IncidentTarget -> acknowledge(target.id, key)
                    is AlarmTarget.EarlyAlertTarget -> check(target.id, key)
                }
                intentKeys.remove(target.id)
                shown.update { it?.copy(phase = CommandPhase.Idle, stoppedLine = stoppedLine) }
                settle(target.id)
            } catch (cause: ApiException) {
                setPhase(CommandPhase.Failed(cause.message))
            }
        }
    }

    private suspend fun acknowledge(id: String, key: String): String {
        val incident = repository.incident(id, userId())
        if (incident.silencesAlarm) return STOPPED_BY_SERVER
        if (incident.status != IncidentStatus.OPENED) {
            setPhase(CommandPhase.Opening)
            repository.openIncident(id, key, userId())
            setPhase(CommandPhase.Sending)
        }
        val acknowledged = repository.acknowledgeIncident(id, key, userId())
        if (acknowledged.acknowledgedAt == null) {
            throw ApiException(
                "NO_ACKNOWLEDGEMENT",
                "Server menjawab tanpa waktu tercatat, jadi Anda belum dapat dianggap menuju lokasi.",
            )
        }
        return "Tercatat menuju lokasi. Alarm berhenti."
    }

    private suspend fun check(id: String, key: String): String {
        val alert = repository.checkAlert(id, key)
        if (!alert.silencesAlarm) {
            throw ApiException("NO_CHECK", "Server belum mencatat pengecekan.")
        }
        return "Pengecekan tercatat server. Alarm berhenti."
    }

    private fun onAlarmsChanged(alarms: List<ActiveAlarm>) {
        val current = shown.value
        val latest = alarms.lastOrNull()
        when {
            current == null -> if (latest != null) show(latest)
            alarms.any { it.target == current.alarm.target } -> Unit
            latest != null -> show(latest)
            current.stoppedLine == null -> shown.update { it?.copy(phase = CommandPhase.Idle, stoppedLine = STOPPED_BY_SERVER) }
        }
    }

    private fun show(alarm: ActiveAlarm) {
        shown.value = Shown(alarm)
        load(alarm.target)
    }

    private fun load(target: AlarmTarget) {
        loading?.cancel()
        loading = viewModelScope.launch {
            try {
                val (details, settled) = when (target) {
                    is AlarmTarget.IncidentTarget -> repository.incident(target.id, userId()).let {
                        AlarmDetails(it.location.name, it.location.detailLine, it.createdAt) to it.silencesAlarm
                    }
                    is AlarmTarget.EarlyAlertTarget -> repository.earlyAlert(target.id).let {
                        AlarmDetails(it.patient.locationName, it.message.orEmpty(), it.createdAt) to it.silencesAlarm
                    }
                }
                shown.update { if (it?.alarm?.target == target) it.copy(details = details) else it }
                if (settled) settle(target.id)
            } catch (_: ApiException) {
            }
        }
    }

    private fun settle(id: String) {
        registry.settle(setOf(id))
        onSettled(id)
    }

    private fun setPhase(phase: CommandPhase) = shown.update { it?.copy(phase = phase) }

    private fun Shown.toUiState(): AlarmUiState {
        val codeBlue = alarm.target is AlarmTarget.IncidentTarget
        return AlarmUiState(
            kind = if (codeBlue) AlarmKind.CODE_BLUE else AlarmKind.EARLY_ALERT,
            headline = if (codeBlue) "Code Blue" else "Early alert kritis",
            location = details?.location ?: alarm.title,
            detail = details?.detail ?: alarm.body,
            createdAt = details?.createdAt ?: alarm.receivedAt,
            waitingLine = if (codeBlue) "Belum ada yang menuju lokasi" else "Menunggu pengecekan",
            primaryLabel = if (codeBlue) "Saya menuju lokasi" else "Cek pasien",
            busyLabel = if (codeBlue) "Memproses…" else "Mencatat…",
            phase = phase,
            stoppedLine = stoppedLine,
        )
    }

    private companion object {
        const val STOPPED_BY_SERVER = "Server menyatakan alert ini sudah ditangani. Alarm berhenti."
    }
}
