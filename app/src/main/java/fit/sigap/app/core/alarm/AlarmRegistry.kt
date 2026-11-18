package fit.sigap.app.core.alarm

import fit.sigap.app.core.push.AlarmTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

data class ActiveAlarm(
    val target: AlarmTarget,
    val title: String,
    val body: String,
    val receivedAt: Instant,
)

class AlarmRegistry {
    private val _alarms = MutableStateFlow<List<ActiveAlarm>>(emptyList())
    val alarms: StateFlow<List<ActiveAlarm>> = _alarms.asStateFlow()

    val latest: ActiveAlarm? get() = _alarms.value.lastOrNull()

    fun ring(alarm: ActiveAlarm) = _alarms.update { current ->
        current.filterNot { it.target == alarm.target } + alarm
    }

    fun settle(ids: Set<String>) {
        if (ids.isEmpty()) return
        _alarms.update { current -> current.filterNot { it.target.id in ids } }
    }

    fun settleAll() = _alarms.update { emptyList() }
}
