package fit.sigap.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fit.sigap.app.core.designsystem.theme.SigapTheme

@Composable
fun SigapTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    placeholder: String? = null,
    required: Boolean = false,
    numeric: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = SigapTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = buildAnnotatedString {
                append(label)
                if (required) {
                    withStyle(MaterialTheme.typography.labelMedium.toSpanStyle().copy(color = colors.signalRed)) {
                        append(" *")
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink950,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            textStyle = if (numeric) SigapTheme.data.md else MaterialTheme.typography.bodyLarge,
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brand,
                unfocusedBorderColor = colors.line500,
                errorBorderColor = colors.signalRed,
                disabledBorderColor = colors.line500.copy(alpha = 0.6f),
                focusedContainerColor = colors.paper0,
                unfocusedContainerColor = colors.paper0,
                errorContainerColor = colors.paper0,
                disabledContainerColor = colors.paper50,
                focusedPlaceholderColor = colors.ink700.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = colors.ink700.copy(alpha = 0.6f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
        )
        when {
            error != null -> Text(error, style = MaterialTheme.typography.labelSmall, color = colors.signalRed)
            hint != null -> Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.ink700)
        }
    }
}
