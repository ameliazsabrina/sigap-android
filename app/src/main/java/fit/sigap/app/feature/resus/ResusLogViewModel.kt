package fit.sigap.app.feature.resus

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fit.sigap.app.core.data.ResusLogRepository
import fit.sigap.app.core.model.ResusAction
import fit.sigap.app.core.model.ResusEntry
import fit.sigap.app.core.model.ResusLogSnapshot
import fit.sigap.app.core.model.ResusSettings
import fit.sigap.app.core.model.ResusSheet
import fit.sigap.app.core.model.Rhythm
import fit.sigap.app.core.model.conflictsWith
import fit.sigap.app.core.model.recentSame
import fit.sigap.app.core.model.toSheet
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.session.SessionUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface ResusPrompt {
    val action: ResusAction

    data class Duplicate(override val action: ResusAction, val previous: ResusEntry) : ResusPrompt

    data class RhythmConflict(override val action: ResusAction, val rhythm: Rhythm) : ResusPrompt

    data class Void(override val action: ResusAction.Correction, val entry: ResusEntry) : ResusPrompt
}

data class UndoOffer(val entryId: String, val label: String, val expiresAt: Instant)

data class ResusLogState(
    val log: ResusLogSnapshot = ResusLogSnapshot(),
    val synced: Boolean = false,
    val syncError: String? = null,
    val saveError: String? = null,
    val prompt: ResusPrompt? = null,
    val undo: UndoOffer? = null,
    val settings: ResusSettings = ResusSettings(),
) {
    val sheet: ResusSheet by lazy { log.entries.toSheet() }
}

class ResusLogViewModel(
    private val incidentId: String,
    private val user: SessionUser,
    private val repository: ResusLogRepository,
    private val now: () -> Instant,
) : ViewModel() {
    private val _state = MutableStateFlow(ResusLogState())
    val state: StateFlow<ResusLogState> = _state.asStateFlow()

    private var polling: Job? = null
    private var undoTimer: Job? = null

    init {
        viewModelScope.launch {
            repository.observe(incidentId).collect { log -> _state.update { it.copy(log = log) } }
        }
        viewModelScope.launch {
            try {
                val settings = repository.settings()
                _state.update { it.copy(settings = settings) }
            } catch (_: ApiException) {
            }
        }
    }

    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(_state.value.settings.pollInterval.toMillis())
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    fun retry() {
        viewModelScope.launch { refresh() }
    }

    fun recordRhythm(rhythm: Rhythm) = record(ResusAction.RhythmChecked(rhythm, startsCycle = false))

    fun record(action: ResusAction) {
        val current = _state.value
        val rhythm = current.sheet.rhythm
        if (action.conflictsWith(rhythm) && rhythm != null) {
            _state.update { it.copy(prompt = ResusPrompt.RhythmConflict(action, rhythm)) }
            return
        }
        recordUnlessDuplicate(action)
    }

    fun requestVoid(entryId: String) {
        val entry = _state.value.log.entries.firstOrNull { it.id == entryId } ?: return
        _state.update { it.copy(prompt = ResusPrompt.Void(ResusAction.Correction(entry.id), entry)) }
    }

    fun confirmPrompt() {
        val prompt = _state.value.prompt ?: return
        _state.update { it.copy(prompt = null) }
        when (prompt) {
            is ResusPrompt.RhythmConflict -> recordUnlessDuplicate(prompt.action)
            is ResusPrompt.Duplicate, is ResusPrompt.Void -> commit(prompt.action)
        }
    }

    fun dismissPrompt() = _state.update { it.copy(prompt = null) }

    fun undo() {
        val offer = _state.value.undo ?: return
        undoTimer?.cancel()
        _state.update { it.copy(undo = null) }
        commit(ResusAction.Correction(offer.entryId, reason = "Diurungkan"))
    }

    fun clearSaveError() = _state.update { it.copy(saveError = null) }

    private fun recordUnlessDuplicate(action: ResusAction) {
        val current = _state.value
        val previous = current.log.entries.recentSame(action, now(), current.settings.duplicateWarning)
        if (previous != null) {
            _state.update { it.copy(prompt = ResusPrompt.Duplicate(action, previous)) }
            return
        }
        commit(action)
    }

    private fun commit(action: ResusAction) {
        viewModelScope.launch {
            val entry = try {
                repository.record(incidentId, user.id, user.name, action)
            } catch (_: SQLiteException) {
                _state.update { it.copy(saveError = "Tindakan gagal disimpan di HP ini. Ketuk lagi.") }
                return@launch
            }
            if (action !is ResusAction.Correction) offerUndo(entry)
        }
    }

    private fun offerUndo(entry: ResusEntry) {
        val window = _state.value.settings.undoWindow
        undoTimer?.cancel()
        _state.update { it.copy(undo = UndoOffer(entry.id, entry.action.label, now().plus(window)), saveError = null) }
        undoTimer = viewModelScope.launch {
            delay(window.toMillis())
            _state.update { if (it.undo?.entryId == entry.id) it.copy(undo = null) else it }
        }
    }

    private suspend fun refresh() {
        try {
            repository.refresh(incidentId)
            _state.update { it.copy(synced = true, syncError = null) }
        } catch (cause: ApiException) {
            _state.update { it.copy(syncError = cause.message) }
        }
    }
}
