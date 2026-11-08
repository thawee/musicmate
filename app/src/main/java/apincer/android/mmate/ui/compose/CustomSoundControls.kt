package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class CustomSoundSignature(val title: String, val detail: String)

internal fun customSoundSignature(bass: Double, treble: Double): CustomSoundSignature {
    val low = if (abs(bass) < 0.5) 0 else if (bass > 0) 1 else -1
    val high = if (abs(treble) < 0.5) 0 else if (treble > 0) 1 else -1
    return when {
        low == 0 && high == 0 -> CustomSoundSignature("Balanced", "Your correction with neutral bass and treble")
        low > 0 && high > 0 -> CustomSoundSignature("Punchy and bright", "More bass weight and clearer high details")
        low > 0 && high < 0 -> CustomSoundSignature("Warm and full", "Fuller bass with gentler high details")
        low < 0 && high > 0 -> CustomSoundSignature("Leaner and brighter", "Lighter bass with clearer high details")
        low > 0 -> CustomSoundSignature("Fuller", "More bass weight on top of your correction")
        high < 0 && low == 0 -> CustomSoundSignature("Softer", "Gentler high details on top of your correction")
        high > 0 -> CustomSoundSignature("Brighter", "Clearer high details on top of your correction")
        low < 0 && high < 0 -> CustomSoundSignature("Light and soft", "Lighter bass with gentler high details")
        else -> CustomSoundSignature("Leaner", "Lighter bass on top of your correction")
    }
}

@Composable
internal fun CustomSoundControls(
    identity: Any?, bassDb: Double, trebleDb: Double, nightListening: Boolean,
    enabled: Boolean = true, rangeDb: Float = 6f, canCompare: Boolean = true,
    onAdjust: (Double, Double) -> Unit,
    onAdjustFinished: (Double, Double) -> Unit = { _, _ -> },
    onCompare: (Boolean) -> Unit = {},
    onPreviewEnd: () -> Unit = {}
) {
    var bass by remember(identity, bassDb) { mutableDoubleStateOf(bassDb) }
    var treble by remember(identity, trebleDb) { mutableDoubleStateOf(trebleDb) }
    var comparing by remember(identity, bassDb, trebleDb, nightListening) { mutableStateOf(false) }
    val comparisonCallback by rememberUpdatedState(onCompare)
    LaunchedEffect(identity, comparing) { comparisonCallback(comparing) }
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    DisposableEffect(identity, lifecycle) {
        val clearComparison = onCompare
        val clearPreview = onPreviewEnd
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                comparing = false; clearComparison(false); clearPreview()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            clearComparison(false); clearPreview()
        }
    }
    val signature = customSoundSignature(bass, treble)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(signature.title, color = MusicMateDesignTokens.Text.Primary, fontSize = 16.sp)
        TuningHint(signature.detail)
        CustomToneSlider("Bass", bass, rangeDb, enabled, {
            comparing = false; comparisonCallback(false); bass = it; onAdjust(bass, treble)
        }, { onAdjustFinished(bass, treble) })
        CustomToneSlider("Treble", treble, rangeDb, enabled, {
            comparing = false; comparisonCallback(false); treble = it; onAdjust(bass, treble)
        }, { onAdjustFinished(bass, treble) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                comparing = false; comparisonCallback(false); bass = 0.0; treble = 0.0
                onAdjust(0.0, 0.0); onAdjustFinished(0.0, 0.0)
            }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Reset") }
            TextButton(onClick = { comparing = !comparing }, enabled = enabled && canCompare,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(if (comparing) "Back to Custom" else "Compare")
            }
        }
        if (comparing) TuningHint("Comparing: correction without Custom adjustments")
    }
}

@Composable
private fun CustomToneSlider(label: String, value: Double, range: Float, enabled: Boolean,
    onChange: (Double) -> Unit, onFinish: () -> Unit) {
    Column {
        Text("$label: %+.1f dB".format(value), color = MusicMateDesignTokens.Text.Primary, fontSize = 13.sp)
        Slider(value = value.toFloat().coerceIn(-range, range),
            onValueChange = { onChange((it * 2).roundToInt() / 2.0) },
            onValueChangeFinished = onFinish, enabled = enabled, valueRange = -range..range,
            steps = (range * 4).roundToInt() - 1,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { contentDescription = "$label adjustment" })
    }
}
