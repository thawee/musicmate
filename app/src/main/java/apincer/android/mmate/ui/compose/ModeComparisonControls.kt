package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Comparison is transient and cleared on dismissal, selection changes and backgrounding. */
@Composable
internal fun ModeComparisonControls(identity: Any?, enabled: Boolean, onCompare: (Boolean?) -> Unit) {
    var side by remember(identity, enabled) { mutableStateOf<Boolean?>(null) }
    val callback by rememberUpdatedState(onCompare)
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    LaunchedEffect(identity, enabled, side) { callback(if (enabled) side else null) }
    DisposableEffect(identity, enabled, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { side = null; callback(null) }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); callback(null) }
    }
    Column {
        TextButton(onClick = { side = if (side == null) false else !side!! }, enabled = enabled) {
            Text(when (side) {
                null -> "Compare levels"
                false -> "Hear correction only"
                true -> "Hear selected mode"
            })
        }
        if (side != null) {
            TuningHint("${if (side == true) "Correction only" else "Selected mode"} · estimated level match at 48 kHz. Song loudness may differ; Night, crossfeed and Quiet listening stay unchanged.")
            TextButton(onClick = { side = null }) { Text("End comparison") }
        }
    }
}
