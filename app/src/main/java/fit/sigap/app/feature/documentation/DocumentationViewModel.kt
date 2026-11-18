package fit.sigap.app.feature.documentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fit.sigap.app.core.data.DocumentationRecord
import fit.sigap.app.core.data.DocumentationRepository
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DocumentationState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val latest: DocumentationRecord? = null,
    val generation: Int = 0,
    val pending: DocumentationPending? = null,
    val message: String? = null,
    val error: String? = null,
    val conflict: Boolean = false,
)

class DocumentationViewModel(
    private val incidentId: String,
    private val repository: DocumentationRepository,
    private val onCompleted: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentationState())
    val state: StateFlow<DocumentationState> = _state.asStateFlow()

    private var createKey: String? = null
    private var completeKey: String? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch { fetch() }
    }

    fun reloadAfterConflict() {
        viewModelScope.launch {
            fetch()
            _state.update { it.copy(conflict = false, error = null, generation = it.generation + 1) }
        }
    }

    fun overwriteAfterConflict(values: DocumentationValues) = write(DocumentationPending.SAVE) {
        val fresh = repository.latest(incidentId)
        _state.update { it.copy(latest = fresh) }
        val draft = ensureDraft(values)
        _state.update { it.copy(latest = draft, message = "Draft tersimpan dan menimpa perubahan orang lain.") }
    }

    fun save(values: DocumentationValues) = write(DocumentationPending.SAVE) {
        val draft = ensureDraft(values)
        _state.update { it.copy(latest = draft, message = "Draft tersimpan. Incident belum dinyatakan selesai.") }
    }

    fun complete(values: DocumentationValues) = write(DocumentationPending.COMPLETE) {
        val draft = ensureDraft(values)
        _state.update { it.copy(latest = draft) }
        val key = completeKey ?: UUID.randomUUID().toString().also { completeKey = it }
        val completed = repository.complete(incidentId, draft.id, draft.version, key, values)
        completeKey = null
        _state.update { it.copy(latest = completed) }
        onCompleted()
    }

    private suspend fun fetch() {
        try {
            val latest = repository.latest(incidentId)
            _state.update { it.copy(loading = false, latest = latest) }
        } catch (cause: ApiException) {
            _state.update { it.copy(loading = false, loadError = cause.message) }
        }
    }

    private suspend fun ensureDraft(values: DocumentationValues): DocumentationRecord {
        val latest = _state.value.latest
        if (latest != null) return repository.update(incidentId, latest.id, latest.version, values)
        val key = createKey ?: UUID.randomUUID().toString().also { createKey = it }
        return repository.create(incidentId, key, values).also { createKey = null }
    }

    private fun write(pending: DocumentationPending, block: suspend () -> Unit) {
        if (_state.value.pending != null) return
        _state.update { it.copy(pending = pending, message = null, error = null, conflict = false) }
        viewModelScope.launch {
            try {
                block()
            } catch (cause: ApiException) {
                _state.update {
                    if (cause.code == ApiException.CODE_VERSION_CONFLICT) it.copy(conflict = true) else it.copy(error = cause.message)
                }
            } finally {
                _state.update { it.copy(pending = null) }
            }
        }
    }
}
