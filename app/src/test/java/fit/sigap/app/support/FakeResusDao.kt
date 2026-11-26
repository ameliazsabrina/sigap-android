package fit.sigap.app.support

import fit.sigap.app.core.database.ResusDao
import fit.sigap.app.core.database.ResusEntryEntity
import fit.sigap.app.core.database.ResusIncidentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeResusDao : ResusDao {
    val rows = MutableStateFlow<Map<String, ResusEntryEntity>>(emptyMap())
    val incidents = MutableStateFlow<Map<String, ResusIncidentEntity>>(emptyMap())

    override fun entries(incidentId: String): Flow<List<ResusEntryEntity>> =
        rows.map { all -> all.values.filter { it.incidentId == incidentId } }

    override fun incident(incidentId: String): Flow<ResusIncidentEntity?> = incidents.map { it[incidentId] }

    override suspend fun incidentNow(incidentId: String): ResusIncidentEntity? = incidents.value[incidentId]

    override fun pending(userId: String): Flow<List<ResusEntryEntity>> = rows.map { all ->
        all.values.filter { it.seq == null && it.rejectedReason == null && it.actorUserId == userId }.sortedBy { it.tappedAt }
    }

    override suspend fun insert(entry: ResusEntryEntity) {
        check(entry.id !in rows.value) { "duplicate id ${entry.id}" }
        rows.update { it + (entry.id to entry) }
    }

    override suspend fun upsertEntries(entries: List<ResusEntryEntity>) {
        rows.update { current -> current + entries.associateBy { it.id } }
    }

    override suspend fun upsertIncident(incident: ResusIncidentEntity) {
        incidents.update { it + (incident.incidentId to incident) }
    }

    override suspend fun reject(id: String, reason: String) {
        rows.update { current ->
            val row = current[id] ?: return@update current
            if (row.seq != null) current else current + (id to row.copy(rejectedReason = reason))
        }
    }

    override suspend fun pruneSent(before: Long) {
        rows.update { current -> current.filterValues { it.seq == null || it.tappedAt >= before } }
    }
}

fun resusEntryJson(
    id: String,
    seq: Long,
    type: String = "DRUG_GIVEN",
    payload: String = """{"drug":"EPINEPHRINE"}""",
    actor: String = "u2",
    actorName: String = "Sari",
    tappedAt: String = "2026-11-23T08:01:00.000Z",
    voids: String? = null,
): String = """{"id":"$id","seq":$seq,"entry_type":"$type","payload":$payload,"actor_user_id":"$actor",
    "actor_name":"$actorName","tapped_at":"$tappedAt","tapped_at_suspect":false,
    "received_at":"2026-11-23T08:01:01.000Z","voids_entry_id":${voids?.let { "\"$it\"" } ?: "null"}}"""

fun resusLogJson(entries: List<String>, lastSeq: Long?): String =
    """{"entries":[${entries.joinToString(",")}],"last_seq":${lastSeq ?: "null"}}"""
