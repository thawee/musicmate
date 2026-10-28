package apincer.android.mmate.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.audio.headphones.AutoEqEntry
import apincer.android.mmate.audio.headphones.AutoEqIndex

/** [entries] is null while the AutoEq index loads. */
@Composable
internal fun AutoEqSearchDialog(
    entries: List<AutoEqEntry>?,
    busy: Boolean,
    error: String?,
    onPick: (AutoEqEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(entries, query) { entries?.let { AutoEqIndex.search(it, query) }.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search AutoEq") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = query, onValueChange = { if (it.length <= 200) query = it }, label = { Text("Headphone model") },
                    singleLine = true, enabled = entries != null && !busy, modifier = Modifier.fillMaxWidth())
                if (entries == null && error == null) Text("Loading the AutoEq headphone list", fontSize = 12.sp)
                if (busy || (entries == null && error == null)) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (entries != null && query.isNotBlank() && results.isEmpty()) Text("No matching headphones.", fontSize = 12.sp)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.path }) { entry ->
                        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !busy, role = Role.Button) { onPick(entry) }
                            .padding(vertical = 6.dp)) {
                            Text(entry.name)
                            Text("by ${entry.source}" + (entry.rig?.let { " on $it" } ?: ""), fontSize = 12.sp)
                        }
                    }
                }
                error?.let { Text(it, color = MusicMateDesignTokens.Status.Destructive, fontSize = 12.sp) }
                Text("Profiles come from the AutoEq project (github.com/jaakkopasanen/AutoEq), measured by oratory1990, crinacle and others. AutoEq covers commercial headphones only; use Create DIY profile for your own pair.", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
