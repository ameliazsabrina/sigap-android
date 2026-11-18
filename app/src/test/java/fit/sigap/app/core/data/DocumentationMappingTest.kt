package fit.sigap.app.core.data

import fit.sigap.app.core.model.CodeBlueForm
import fit.sigap.app.core.model.CodeBlueFormRow
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.model.ResponseAnswer
import fit.sigap.app.core.model.TextAnswer
import fit.sigap.app.core.network.ApiJson
import fit.sigap.app.core.network.DocumentationDto
import fit.sigap.app.core.network.DocumentationRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentationMappingTest {
    @Test
    fun `isian dikirim dengan kunci server dan tidak diketahui dipisah`() {
        val request = DocumentationValues(
            answer = ResponseAnswer.UNKNOWN,
            actions = TextAnswer("  CPR  "),
            obstacles = TextAnswer("ketikan tersimpan", unknown = true),
            notes = "catatan",
        ).toRequest(expectedVersion = 3)

        assertEquals(listOf("response_completed", "obstacles"), request.unknownInformation)
        assertEquals("CPR", request.userConfirmedInformation["actions_taken"]?.jsonPrimitive?.content)
        assertFalse("obstacles" in request.userConfirmedInformation)
        assertFalse("code_blue_form" in request.userConfirmedInformation)
    }

    @Test
    fun `formulir memakai snake case, membawa outcome null, dan membuang baris kosong`() {
        val request = DocumentationValues(
            answer = ResponseAnswer.YES,
            form = CodeBlueForm(
                cprStartedAt = "08:10",
                rows = listOf(CodeBlueFormRow(), CodeBlueFormRow(drugName = "Obat simulasi")),
            ),
        ).toRequest(null)

        val form = request.userConfirmedInformation["code_blue_form"]!!.jsonObject
        assertEquals("08:10", form["cpr_started_at"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, form["outcome"])
        assertEquals(1, form["rows"]!!.jsonArray.size)
        assertEquals("Obat simulasi", form["rows"]!!.jsonArray[0].jsonObject["drug_name"]?.jsonPrimitive?.content)
        assertEquals("true", request.userConfirmedInformation["response_completed"]?.jsonPrimitive?.content)
    }

    @Test
    fun `body draft baru tidak membawa expected_version`() {
        val body = ApiJson.encodeToString(DocumentationRequest.serializer(), DocumentationValues().toRequest(null))
        assertFalse("expected_version" in body)
        val versioned = ApiJson.encodeToString(DocumentationRequest.serializer(), DocumentationValues().toRequest(4))
        assertTrue("\"expected_version\":4" in versioned)
    }

    @Test
    fun `jawaban server kembali menjadi isian form`() {
        val dto = ApiJson.decodeFromString<DocumentationDto>(
            """{"id":"d1","documentation_status":"COMPLETED","version":2,"completed_at":"2026-11-05T09:00:00.000Z",
            "notes":"n","unknown_information":["obstacles"],
            "user_confirmed_information":{"response_completed":false,"actions_taken":"CPR",
            "code_blue_form":{"cpr_started_at":"08:10","outcome":"ROSC","failure_types":["NAFAS"],"rows":[{"drug_name":"X"}]}}}""",
        )

        val record = dto.toRecord()

        assertTrue(record.completed)
        assertEquals(ResponseAnswer.NO, record.values.answer)
        assertEquals("CPR", record.values.actions.text)
        assertTrue(record.values.obstacles.unknown)
        assertEquals("ROSC", record.values.form.outcome)
        assertEquals(setOf("NAFAS"), record.values.form.failureTypes)
        assertEquals("X", record.values.form.rows.single().drugName)
    }
}
