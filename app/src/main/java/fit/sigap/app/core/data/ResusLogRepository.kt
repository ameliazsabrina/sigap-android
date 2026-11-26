package fit.sigap.app.core.data

import fit.sigap.app.core.database.ResusDao
import fit.sigap.app.core.database.ResusEntryEntity
import fit.sigap.app.core.database.ResusIncidentEntity
import fit.sigap.app.core.model.ResusAction
import fit.sigap.app.core.model.ResusDrug
import fit.sigap.app.core.model.ResusEntry
import fit.sigap.app.core.model.ResusEntryType
import fit.sigap.app.core.model.ResusLogSnapshot
import fit.sigap.app.core.model.ResusOutcome
import fit.sigap.app.core.model.ResusProcedure
import fit.sigap.app.core.model.ResusSettings
import fit.sigap.app.core.model.Rhythm
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.ApiJson
import fit.sigap.app.core.network.ResusAppendRequest
import fit.sigap.app.core.network.ResusEntryDto
import fit.sigap.app.core.network.ResusEntryInput
import fit.sigap.app.core.network.ResusLogDto
import fit.sigap.app.core.network.ServerClock
import fit.sigap.app.core.network.SigapApi
import fit.sigap.app.core.network.apiCall
import fit.sigap.app.core.network.contractError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

data class ResusWire(val type: String, val payload: JsonObject, val voidsEntryId: String?)

fun ResusAction.toWire(): ResusWire = when (this) {
    is ResusAction.RhythmChecked -> ResusWire(
        ResusEntryType.RHYTHM_CHECKED.name,
        buildJsonObject {
            put("rhythm", rhythm.name)
            put("starts_cycle", startsCycle)
        },
        null,
    )
    ResusAction.CprCycleStarted -> ResusWire(ResusEntryType.CPR_CYCLE_STARTED.name, JsonObject(emptyMap()), null)
    is ResusAction.DrugGiven -> ResusWire(ResusEntryType.DRUG_GIVEN.name, buildJsonObject { put("drug", drug.name) }, null)
    ResusAction.ShockDelivered -> ResusWire(ResusEntryType.SHOCK_DELIVERED.name, JsonObject(emptyMap()), null)
    is ResusAction.ProcedureDone -> ResusWire(
        ResusEntryType.PROCEDURE_DONE.name,
        buildJsonObject {
            put("procedure", procedure.name)
            text?.let { put("text", it) }
        },
        null,
    )
    is ResusAction.NoteAdded -> ResusWire(ResusEntryType.NOTE_ADDED.name, buildJsonObject { put("text", text) }, null)
    is ResusAction.OutcomeRecorded -> ResusWire(
        ResusEntryType.OUTCOME_RECORDED.name,
        buildJsonObject { put("outcome", outcome.name) },
        null,
    )
    is ResusAction.Correction -> ResusWire(
        ResusEntryType.CORRECTION.name,
        buildJsonObject { reason?.let { put("reason", it) } },
        voidsEntryId,
    )
    is ResusAction.Unrecognized -> throw IllegalArgumentException("$this cannot be recorded")
}

private inline fun <reified T : Enum<T>> JsonObject.enum(key: String): T? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun parseResusAction(type: String, payload: JsonObject, voidsEntryId: String?): ResusAction {
    val unrecognized = ResusAction.Unrecognized(type)
    return when (enumValues<ResusEntryType>().firstOrNull { it.name == type }) {
        ResusEntryType.RHYTHM_CHECKED -> {
            val rhythm = payload.enum<Rhythm>("rhythm") ?: return unrecognized
            val startsCycle = (payload["starts_cycle"] as? JsonPrimitive)?.booleanOrNull ?: return unrecognized
            ResusAction.RhythmChecked(rhythm, startsCycle)
        }
        ResusEntryType.CPR_CYCLE_STARTED -> ResusAction.CprCycleStarted
        ResusEntryType.DRUG_GIVEN -> payload.enum<ResusDrug>("drug")?.let(ResusAction::DrugGiven) ?: unrecognized
        ResusEntryType.SHOCK_DELIVERED -> ResusAction.ShockDelivered
        ResusEntryType.PROCEDURE_DONE -> payload.enum<ResusProcedure>("procedure")
            ?.let { ResusAction.ProcedureDone(it, payload.text("text")) } ?: unrecognized
        ResusEntryType.NOTE_ADDED -> payload.text("text")?.let(ResusAction::NoteAdded) ?: unrecognized
        ResusEntryType.OUTCOME_RECORDED -> payload.enum<ResusOutcome>("outcome")?.let(ResusAction::OutcomeRecorded) ?: unrecognized
        ResusEntryType.CORRECTION -> voidsEntryId?.let { ResusAction.Correction(it, payload.text("reason")) } ?: unrecognized
        null -> unrecognized
    }
}

private fun instant(value: String): Instant = try {
    Instant.parse(value)
} catch (_: DateTimeParseException) {
    throw contractError()
}

internal fun ResusEntryDto.toEntity(incidentId: String) = ResusEntryEntity(
    id = id,
    incidentId = incidentId,
    seq = seq,
    entryType = entryType,
    payload = payload.toString(),
    voidsEntryId = voidsEntryId,
    actorUserId = actorUserId,
    actorName = actorName,
    tappedAt = instant(tappedAt).toEpochMilli(),
    clockOffsetMs = 0,
    tappedAtSuspect = tappedAtSuspect,
    receivedAt = instant(receivedAt).toEpochMilli(),
    rejectedReason = null,
)

internal fun ResusEntryEntity.toDomain(): ResusEntry {
    val json = try {
        ApiJson.decodeFromString<JsonObject>(payload)
    } catch (_: SerializationException) {
        JsonObject(emptyMap())
    }
    return ResusEntry(
        id = id,
        incidentId = incidentId,
        seq = seq,
        action = parseResusAction(entryType, json, voidsEntryId),
        actorUserId = actorUserId,
        actorName = actorName,
        tappedAt = Instant.ofEpochMilli(tappedAt),
        tappedAtSuspect = tappedAtSuspect,
        receivedAt = receivedAt?.let(Instant::ofEpochMilli),
        rejectedReason = rejectedReason,
    )
}

private fun ResusEntryEntity.toInput() = ResusEntryInput(
    id = id,
    entryType = entryType,
    payload = ApiJson.decodeFromString(payload),
    tappedAt = Instant.ofEpochMilli(tappedAt).toString(),
    clockOffsetMs = clockOffsetMs,
    voidsEntryId = voidsEntryId,
)

private val RetryableStatus = setOf(401, 408, 429)

private fun ApiException.isRetryable(): Boolean = isNetwork || status == null || status >= 500 || status in RetryableStatus

private val SentRetention: Duration = Duration.ofDays(7)

class ResusLogRepository(
    private val api: SigapApi,
    private val dao: ResusDao,
    private val clock: ServerClock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observe(incidentId: String): Flow<ResusLogSnapshot> =
        combine(dao.entries(incidentId), dao.incident(incidentId)) { entries, incident ->
            ResusLogSnapshot(entries = entries.map { it.toDomain() }, lastSeq = incident?.lastSeq)
        }

    fun pending(userId: String): Flow<List<ResusEntryEntity>> = dao.pending(userId)

    suspend fun record(incidentId: String, userId: String, userName: String, action: ResusAction): ResusEntry {
        val wire = action.toWire()
        val entity = ResusEntryEntity(
            id = newId(),
            incidentId = incidentId,
            seq = null,
            entryType = wire.type,
            payload = wire.payload.toString(),
            voidsEntryId = wire.voidsEntryId,
            actorUserId = userId,
            actorName = userName,
            tappedAt = clock.now().toEpochMilli(),
            clockOffsetMs = clock.offsetMs,
            tappedAtSuspect = false,
            receivedAt = null,
            rejectedReason = null,
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    suspend fun refresh(incidentId: String) {
        val after = dao.incidentNow(incidentId)?.lastSeq
        val page = apiCall { api.resusLog(incidentId, after) }.data
        apply(incidentId, page, advanceCursor = true)
    }

    suspend fun send(incidentId: String, batch: List<ResusEntryEntity>) {
        if (batch.isEmpty()) return
        try {
            post(incidentId, batch)
        } catch (cause: ApiException) {
            if (cause.isRetryable()) throw cause
            if (batch.size == 1) {
                dao.reject(batch.single().id, cause.message)
            } else {
                batch.forEach { send(incidentId, listOf(it)) }
            }
        }
    }

    suspend fun settings(): ResusSettings {
        val values = apiCall { api.responseSettings() }.data.associate { it.key to it.value }
        val defaults = ResusSettings()
        return ResusSettings(
            duplicateWarning = values["resus_duplicate_warning_seconds"]?.let(Duration::ofSeconds) ?: defaults.duplicateWarning,
            pollInterval = values["resus_log_poll_seconds"]?.let(Duration::ofSeconds) ?: defaults.pollInterval,
            undoWindow = values["resus_undo_seconds"]?.let(Duration::ofSeconds) ?: defaults.undoWindow,
        )
    }

    suspend fun pruneOld(now: Instant = Instant.now()) {
        dao.pruneSent(now.minus(SentRetention).toEpochMilli())
    }

    private suspend fun post(incidentId: String, batch: List<ResusEntryEntity>) {
        val page = apiCall { api.appendResusLog(incidentId, ResusAppendRequest(batch.map { it.toInput() })) }.data
        apply(incidentId, page, advanceCursor = false)
    }

    private suspend fun apply(incidentId: String, page: ResusLogDto, advanceCursor: Boolean) {
        val rows = page.entries.map { it.toEntity(incidentId) }
        val current = dao.incidentNow(incidentId)
        val cursor = if (advanceCursor) {
            listOfNotNull(current?.lastSeq, page.lastSeq, rows.maxOfOrNull { it.seq ?: 0 }).maxOrNull()
        } else {
            current?.lastSeq
        }
        dao.applyServer(rows, ResusIncidentEntity(incidentId = incidentId, lastSeq = cursor))
    }
}
