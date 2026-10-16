package dev.glinesb.openmacros.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Collapsible "How do I get this?" with numbered steps, a cost note and a docs link. */
@Composable
fun ApiKeyHelp(steps: List<String>, costNote: String, docsLabel: String, docsUrl: String) {
    val context = LocalContext.current
    var open by rememberSaveable(docsUrl) { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) {
            Text("How do I get this?")
            Spacer(Modifier.width(4.dp))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 4.dp)) {
                steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp))
                        Text(s, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(costNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { openLink(context, docsUrl) }) { Text(docsLabel) }
            }
        }
    }
}
