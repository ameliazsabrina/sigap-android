package fit.sigap.app.core.data

import fit.sigap.app.core.model.CodeBlueForm
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.model.ResponseAnswer
import fit.sigap.app.core.model.TextAnswer
import fit.sigap.app.core.network.DocumentationDto
import fit.sigap.app.core.network.DocumentationRequest
import fit.sigap.app.core.network.SigapApi
import fit.sigap.app.core.network.apiCall
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Instant

private const val RESPONSE_COMPLETED = "response_completed"
private const val ACTIONS_TAKEN = "actions_taken"
private const val OBSTACLES = "obstacles"
private const val CODE_BLUE_FORM = "code_blue_form"
private const val STATUS_COMPLETED = "COMPLETED"

@OptIn(ExperimentalSerializationApi::class)
private val FormJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

data class DocumentationRecord(
    val id: String,
    val version: Int,
    val completed: Boolean,
    val completedAt: Instant?,
    val values: DocumentationValues,
)

fun DocumentationDto.toRecord(): DocumentationRecord {
    val confirmed = userConfirmedInformation ?: JsonObject(emptyMap())
    fun text(key: String) = TextAnswer(
        text = (confirmed[key] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        unknown = key in unknownInformation,
    )
    return DocumentationRecord(
        id = id,
        version = version,
        completed = documentationStatus == STATUS_COMPLETED,
        completedAt = completedAt?.let(Instant::parse),
        values = DocumentationValues(
            answer = when {
                RESPONSE_COMPLETED in unknownInformation -> ResponseAnswer.UNKNOWN
                (confirmed[RESPONSE_COMPLETED] as? JsonPrimitive)?.booleanOrNull == true -> ResponseAnswer.YES
                (confirmed[RESPONSE_COMPLETED] as? JsonPrimitive)?.booleanOrNull == false -> ResponseAnswer.NO
                else -> null
            },
            actions = text(ACTIONS_TAKEN),
            obstacles = text(OBSTACLES),
            notes = notes.orEmpty(),
            form = confirmed[CODE_BLUE_FORM]?.let {
                try {
                    FormJson.decodeFromJsonElement<CodeBlueForm>(it)
                } catch (_: SerializationException) {
                    null
                }
            } ?: CodeBlueForm(),
        ),
    )
}

fun DocumentationValues.toRequest(expectedVersion: Int?): DocumentationRequest {
    val unknown = buildList {
        if (answer == ResponseAnswer.UNKNOWN) add(RESPONSE_COMPLETED)
        if (actions.unknown) add(ACTIONS_TAKEN)
        if (obstacles.unknown) add(OBSTACLES)
    }
    val confirmed = buildJsonObject {
        when (answer) {
            ResponseAnswer.YES -> put(RESPONSE_COMPLETED, JsonPrimitive(true))
            ResponseAnswer.NO -> put(RESPONSE_COMPLETED, JsonPrimitive(false))
            else -> Unit
        }
        if (!actions.unknown && actions.text.isNotBlank()) put(ACTIONS_TAKEN, JsonPrimitive(actions.text.trim()))
        if (!obstacles.unknown && obstacles.text.isNotBlank()) put(OBSTACLES, JsonPrimitive(obstacles.text.trim()))
        if (form != CodeBlueForm()) {
            val rows = form.rows.filter { row ->
                listOf(row.time, row.drugName, row.dose, row.route, row.joule, row.bp, row.hr, row.rr, row.rhythm, row.note)
                    .any(String::isNotBlank)
            }
            put(CODE_BLUE_FORM, FormJson.encodeToJsonElement(form.copy(rows = rows)))
        }
    }
    return DocumentationRequest(confirmed, unknown, notes, expectedVersion)
}

class DocumentationRepository(private val api: SigapApi) {
    suspend fun latest(incidentId: String): DocumentationRecord? =
        apiCall { api.documentation(incidentId) }.data.firstOrNull()?.toRecord()

    suspend fun create(incidentId: String, key: String, values: DocumentationValues): DocumentationRecord =
        apiCall { api.createDocumentation(incidentId, key, values.toRequest(null)) }.data.toRecord()

    suspend fun update(incidentId: String, id: String, version: Int, values: DocumentationValues): DocumentationRecord =
        apiCall { api.updateDocumentation(incidentId, id, values.toRequest(version)) }.data.toRecord()

    suspend fun complete(incidentId: String, id: String, version: Int, key: String, values: DocumentationValues): DocumentationRecord =
        apiCall { api.completeDocumentation(incidentId, id, key, values.toRequest(version)) }.data.toRecord()
}
