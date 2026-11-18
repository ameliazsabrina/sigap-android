package fit.sigap.app.core.model

import kotlinx.serialization.Serializable

object CodeBlueFormLimits {
    const val MAX_ROWS = 40
    const val MAX_TEXT = 500
    const val MAX_CELL = 120
    const val MAX_ANSWER = 2000
    const val MAX_NOTES = 4000
}

@Serializable
data class CodeBlueFormRow(
    val time: String = "",
    val drugName: String = "",
    val dose: String = "",
    val route: String = "",
    val joule: String = "",
    val bp: String = "",
    val hr: String = "",
    val rr: String = "",
    val rhythm: String = "",
    val note: String = "",
)

@Serializable
data class CodeBlueForm(
    val cprStartedAt: String = "",
    val failureTypes: Set<String> = emptySet(),
    val place: String = "",
    val airway: Set<String> = emptySet(),
    val breathing: Set<String> = emptySet(),
    val rhythm: Set<String> = emptySet(),
    val primaryDiagnosis: String = "",
    val secondaryDiagnosis: String = "",
    val rows: List<CodeBlueFormRow> = emptyList(),
    val cprStoppedAt: String = "",
    val outcome: String? = null,
    val declaredDeadBy: String = "",
)

@Serializable
enum class ResponseAnswer { YES, NO, UNKNOWN }

@Serializable
data class TextAnswer(val text: String = "", val unknown: Boolean = false)

@Serializable
data class DocumentationValues(
    val answer: ResponseAnswer? = null,
    val actions: TextAnswer = TextAnswer(),
    val obstacles: TextAnswer = TextAnswer(),
    val notes: String = "",
    val form: CodeBlueForm = CodeBlueForm(),
) {
    val unknownItems: List<String>
        get() = listOfNotNull(
            "Status penyelesaian respons".takeIf { answer == ResponseAnswer.UNKNOWN },
            "Tindakan yang dilakukan".takeIf { actions.unknown },
            "Kendala selama respons".takeIf { obstacles.unknown },
        )
}
