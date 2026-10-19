package dev.glinesb.openmacros.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    suffix: String? = null,
    decimal: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
    allowNegative: Boolean = false,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            val body = if (allowNegative) v.removePrefix("-") else v
            if (body.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }) onValueChange(v)
        },
        placeholder = placeholder?.let { { Text(it) } },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}
