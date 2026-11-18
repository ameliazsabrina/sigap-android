package fit.sigap.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.designsystem.theme.tint

data class ChoiceOption<T>(val value: T, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChoiceChips(
    options: List<ChoiceOption<T>>,
    selected: Set<T>,
    onChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SigapTheme.colors
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        options.forEach { option ->
            val isOn = option.value in selected
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (isOn) colors.brand.tint() else colors.paper0)
                    .border(1.dp, if (isOn) colors.brand else colors.line500, CircleShape)
                    .toggleable(
                        value = isOn,
                        role = Role.Checkbox,
                        onValueChange = { on ->
                            onChange(if (on) selected + option.value else selected - option.value)
                        },
                    )
                    .padding(horizontal = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isOn) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOn) colors.brand else colors.ink950,
                )
            }
        }
    }
}

@Composable
fun <T> RadioList(
    options: List<ChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected == option.value,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.value) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                RadioButton(
                    selected = selected == option.value,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = SigapTheme.colors.brand,
                        unselectedColor = SigapTheme.colors.line500,
                    ),
                )
                Text(option.label, style = MaterialTheme.typography.bodyMedium, color = SigapTheme.colors.ink950)
            }
        }
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = SigapTheme.colors.brand,
                uncheckedColor = SigapTheme.colors.line500,
            ),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SigapTheme.colors.ink950)
    }
}
