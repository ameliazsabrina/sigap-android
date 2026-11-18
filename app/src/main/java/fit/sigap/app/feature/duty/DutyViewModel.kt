package fit.sigap.app.feature.duty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fit.sigap.app.core.data.DutyRepository
import fit.sigap.app.core.data.WardPatients
import fit.sigap.app.core.model.ConnectionState
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.IncidentStatus
import fit.sigap.app.core.model.PatientVitals
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.core.model.Shift
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.push.PushRepository
import fit.sigap.app.core.push.PushStatus
import fit.sigap.app.core.session.SessionUser
import fit.sigap.app.feature.common.CommandPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.UUID

data class DutyState(
    val shiftKnown: Boolean = false,
    val shift: Shift? = null,
    val shiftError: String? = null,
    val dutyPhase: CommandPhase = CommandPhase.Idle,
    val signOutPhase: CommandPhase = CommandPhase.Idle,
    val connection: ConnectionState = ConnectionState.LIVE,
    val readiness: Readiness = Readiness(),
    val incidents: List<Incident> = emptyList(),
    val alerts: List<EarlyAlert> = emptyList(),
    val wardPatients: WardPatients = WardPatients(0, 0),
    val arrivalLimit: Duration? = null,
    val phases: Map<String, CommandPhase> = emptyMap(),
    val pushTestPhase: CommandPhase = CommandPhase.Idle,
    val pushTestSent: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

private const val POLL_INTERVAL_MS = 10_000L
private const val SHIFT_RECHECK_TICKS = 3
private const val CHECK_IN_PREFIX = "SIGAP-ABSEN:"
private val BackupCode = Regex("^\\d{6}$")

class DutyViewModel(
    private val user: SessionUser,
    private val repository: DutyRepository,
    private val push: PushRepository,
    private val registerPush: suspend () -> PushStatus = push::register,
    private val cancelSettledAlarms: (ids: Set<String>) -> Unit = {},
    private val logout: suspend () -> Unit = {},
    private val readReadiness: (pushConfirmed: Boolean) -> Readiness,
) : ViewModel() {
    private val _state = MutableStateFlow(DutyState())
    val state: StateFlow<DutyState> = _state.asStateFlow()

    private val intentKeys = mutableMapOf<String, String>()
    private var polling: Job? = null
    private var pushConfirmed = false

    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            launch { loadOnce() }
            var tick = 0
            while (isActive) {
                if (tick % SHIFT_RECHECK_TICKS == 0 || !_state.value.shiftKnown) refreshShift()
                if (_state.value.shift != null) refreshData()
                if (tick % SHIFT_RECHECK_TICKS == 0) refreshPushStatus()
                refreshReadiness()
                tick++
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    fun refreshReadiness() {
        _state.update { it.copy(readiness = readReadiness(pushConfirmed)) }
    }

    fun retry() {
        viewModelScope.launch {
            refreshShift()
            if (_state.value.shift != null) refreshData()
        }
    }

    suspend fun vitals(patientId: String): PatientVitals = repository.vitals(patientId)

    fun checkIn(input: String) {
        val code = input.trim()
        if (!code.startsWith(CHECK_IN_PREFIX) && !BackupCode.matches(code)) {
            _state.update {
                it.copy(dutyPhase = CommandPhase.Failed("QR ini bukan QR absen. Pindai QR absen di layar nurse station."))
            }
            return
        }
        dutyCommand {
            val shift = repository.checkIn(code)
            _state.update { it.copy(shift = shift, shiftKnown = true, shiftError = null) }
            refreshData()
        }
    }

    fun finishDuty() = dutyCommand {
        repository.checkOut()
        _state.update { it.copy(shift = null, incidents = emptyList(), alerts = emptyList(), loaded = false) }
    }

    fun clearDutyError() = _state.update { it.copy(dutyPhase = CommandPhase.Idle) }

    fun signOut() {
        val current = _state.value
        if (current.signOutPhase.isBusy) return
        if (current.shift != null) {
            _state.update { it.copy(signOutPhase = CommandPhase.Failed("Selesai bertugas dulu sebelum keluar dari HP ini.")) }
            return
        }
        _state.update { it.copy(signOutPhase = CommandPhase.Sending) }
        viewModelScope.launch {
            try {
                logout()
                _state.update { it.copy(signOutPhase = CommandPhase.Idle) }
            } catch (cause: ApiException) {
                _state.update { it.copy(signOutPhase = CommandPhase.Failed(cause.message)) }
            }
        }
    }

    fun clearSignOutError() = _state.update { it.copy(signOutPhase = CommandPhase.Idle) }

    fun sendPushTest() {
        if (_state.value.pushTestPhase.isBusy) return
        _state.update { it.copy(pushTestPhase = CommandPhase.Sending, pushTestSent = false) }
        viewModelScope.launch {
            try {
                registerPush()
                val result = push.sendTest()
                _state.update {
                    if (result.sent) {
                        it.copy(pushTestPhase = CommandPhase.Idle, pushTestSent = true)
                    } else {
                        it.copy(pushTestPhase = CommandPhase.Failed(result.reason ?: "Server gagal mengirim notifikasi uji. Coba lagi."))
                    }
                }
            } catch (cause: ApiException) {
                _state.update { it.copy(pushTestPhase = CommandPhase.Failed(cause.message)) }
            }
        }
    }

    fun confirmPushTest() {
        if (_state.value.pushTestPhase.isBusy) return
        _state.update { it.copy(pushTestPhase = CommandPhase.Sending) }
        viewModelScope.launch {
            try {
                push.confirmTest()
                pushConfirmed = push.status().confirmed
                refreshReadiness()
                _state.update {
                    if (pushConfirmed) {
                        it.copy(pushTestPhase = CommandPhase.Idle, pushTestSent = false)
                    } else {
                        it.copy(pushTestPhase = CommandPhase.Failed("Server belum mencatat konfirmasi. Kirim ulang notifikasi uji."))
                    }
                }
            } catch (cause: ApiException) {
                _state.update { it.copy(pushTestPhase = CommandPhase.Failed(cause.message)) }
            }
        }
    }

    fun acknowledge(id: String, onDone: () -> Unit = {}) {
        val incident = _state.value.incidents.firstOrNull { it.id == id } ?: return
        val opensFirst = !incident.isAcknowledged && incident.status != IncidentStatus.OPENED
        command(id, "acknowledge", startPhase = if (opensFirst) CommandPhase.Opening else CommandPhase.Sending, onDone = onDone) { key ->
            if (opensFirst) {
                replaceIncident(repository.openIncident(id, key, user.id))
                setPhase(id, CommandPhase.Sending)
            }
            val acknowledged = repository.acknowledgeIncident(id, key, user.id)
            if (acknowledged.acknowledgedAt == null) {
                throw ApiException(
                    "NO_ACKNOWLEDGEMENT",
                    "Server menjawab tanpa waktu tercatat, jadi Anda belum dapat dianggap menuju lokasi. Coba lagi.",
                )
            }
            replaceIncident(acknowledged)
        }
    }

    fun arrive(id: String) = command(id, "arrive") { key -> replaceIncident(repository.arriveIncident(id, key, user.id)) }

    fun resolve(id: String) = command(id, "resolve") { key -> replaceIncident(repository.resolveIncident(id, key, user.id)) }

    fun check(id: String, onDone: () -> Unit = {}) = command(id, "check", onDone = onDone) { key ->
        replaceAlert(repository.checkAlert(id, key))
    }

    fun close(id: String, reason: String) = command(id, "close") { key ->
        repository.closeAlert(id, key, reason)
        refreshData()
    }

    fun escalate(id: String, reason: String) = command(id, "escalate") { key ->
        replaceIncident(repository.escalateAlert(id, key, reason, user.id))
        refreshData()
    }

    private fun command(
        id: String,
        intent: String,
        startPhase: CommandPhase = CommandPhase.Sending,
        onDone: () -> Unit = {},
        block: suspend (key: String) -> Unit,
    ) {
        if (_state.value.phases[id]?.isBusy == true) return
        val slot = "$id:$intent"
        val key = intentKeys.getOrPut(slot) { UUID.randomUUID().toString() }
        setPhase(id, startPhase)
        viewModelScope.launch {
            try {
                block(key)
                intentKeys.remove(slot)
                setPhase(id, CommandPhase.Idle)
                onDone()
            } catch (cause: ApiException) {
                setPhase(id, CommandPhase.Failed(cause.message))
            }
        }
    }

    private fun dutyCommand(block: suspend () -> Unit) {
        if (_state.value.dutyPhase.isBusy) return
        _state.update { it.copy(dutyPhase = CommandPhase.Sending) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(dutyPhase = CommandPhase.Idle) }
            } catch (cause: ApiException) {
                _state.update { it.copy(dutyPhase = CommandPhase.Failed(cause.message)) }
            }
        }
    }

    private suspend fun loadOnce() {
        coroutineScope {
            launch {
                try {
                    val limit = repository.arrivalLimit()
                    _state.update { it.copy(arrivalLimit = limit) }
                } catch (_: ApiException) {
                }
            }
        }
    }

    private suspend fun refreshPushStatus() {
        try {
            pushConfirmed = push.status().confirmed
            refreshReadiness()
        } catch (_: ApiException) {
        }
    }

    private suspend fun refreshShift() {
        try {
            val shift = repository.myShift()
            _state.update { it.copy(shift = shift, shiftKnown = true, shiftError = null) }
        } catch (cause: ApiException) {
            _state.update { it.copy(shiftError = cause.message) }
        }
    }

    private suspend fun refreshData() {
        val shift = _state.value.shift ?: return
        try {
            coroutineScope {
                val jobs = buildList {
                    add(async { _state.update { state -> state.copy(incidents = repository.incidents(user.id).inScope(shift)) } })
                    add(async { _state.update { state -> state.copy(alerts = repository.earlyAlerts()) } })
                    if (user.role == DutyRole.NURSE) {
                        add(async { _state.update { state -> state.copy(wardPatients = repository.wardPatients(shift.wardId)) } })
                    }
                }
                jobs.awaitAll()
            }
            _state.update { it.copy(loaded = true, error = null, connection = ConnectionState.LIVE) }
            cancelSettled()
        } catch (cause: ApiException) {
            _state.update { it.copy(error = cause.message, connection = ConnectionState.POLLING) }
        }
    }

    private fun List<Incident>.inScope(shift: Shift): List<Incident> =
        if (user.role == DutyRole.NURSE) filter { it.patient.wardId == shift.wardId } else this

    private fun setPhase(id: String, phase: CommandPhase) = _state.update { it.copy(phases = it.phases + (id to phase)) }

    private fun replaceIncident(incident: Incident) {
        _state.update { current ->
            val exists = current.incidents.any { it.id == incident.id }
            current.copy(
                incidents = if (exists) current.incidents.map { if (it.id == incident.id) incident else it } else current.incidents + incident,
            )
        }
        cancelSettled()
    }

    private fun replaceAlert(alert: EarlyAlert) {
        _state.update { current -> current.copy(alerts = current.alerts.map { if (it.id == alert.id) alert else it }) }
        cancelSettled()
    }

    private fun cancelSettled() {
        val current = _state.value
        cancelSettledAlarms(
            current.incidents.filter { it.silencesAlarm }.mapTo(mutableSetOf()) { it.id } +
                current.alerts.filter { it.silencesAlarm }.map { it.id },
        )
    }
}
