package apincer.android.mmate.ui.compose

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import apincer.android.mmate.R
import apincer.music.core.codec.FFMpegHelper
import apincer.music.core.model.Track
import apincer.music.core.playback.Loudness
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Read-only measurements, separate from the sampled source-authenticity report. */
object AudioAnalysisDialog {
    @JvmStatic
    fun show(context: Context, track: Track, verifySource: Runnable) {
        val dialog = ComponentDialog(context, R.style.AlertDialogTheme)
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(dialog)
            setViewTreeSavedStateRegistryOwner(dialog)
            setContent {
                MusicMateTheme {
                    AudioAnalysisContent(track, { dialog.dismiss() }) {
                        dialog.dismiss()
                        verifySource.run()
                    }
                }
            }
        }
        dialog.setContentView(view)
        dialog.window?.apply {
            decorView.setViewTreeLifecycleOwner(dialog)
            decorView.setViewTreeSavedStateRegistryOwner(dialog)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }
}

/** Each dialog owns its session; closing it must not cancel playback or another analysis. */
private suspend fun measureAudio(track: Track): Loudness? = suspendCancellableCoroutine { continuation ->
    val cancel = FFMpegHelper.measureLoudnessAsync(track.path, track.audioDuration) { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    continuation.invokeOnCancellation { cancel.run() }
}

@Composable
private fun AudioAnalysisContent(track: Track, onClose: () -> Unit, onVerify: () -> Unit) {
    var measurement by remember { mutableStateOf<Loudness?>(null) }
    var measuring by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(track.path, attempt) {
        measuring = true
        measurement = measureAudio(track)
        measuring = false
    }
    AudioAnalysisReport(track, measurement, measuring, expanded,
        { expanded = !expanded }, { attempt++ }, onClose, onVerify)
}

@Composable
internal fun AudioAnalysisReport(
    track: Track,
    measurement: Loudness?,
    measuring: Boolean,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onVerify: () -> Unit
) {
    Surface(shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.audio_analysis_title), style = MaterialTheme.typography.titleLarge)
            Text(listOf(track.title, track.artist).filter { !it.isNullOrBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            MeasurementRow(stringResource(R.string.audio_analysis_dynamic_range),
                if (track.drScore.isFinite() && track.drScore > 0)
                    stringResource(R.string.audio_analysis_dr_value, track.drScore)
                else stringResource(R.string.audio_analysis_dr_missing))
            if (measuring) {
                CircularProgressIndicator()
                Text(stringResource(R.string.audio_analysis_measuring))
            } else {
                val result = measurement
                if (result == null) {
                    Text(stringResource(R.string.audio_analysis_unavailable))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.audio_analysis_retry)) }
                } else {
                    MeasurementRow(stringResource(R.string.audio_analysis_loudness),
                        stringResource(R.string.audio_analysis_lufs, result.integratedLufs))
                    Text(stringResource(peakDescription(result.truePeakDbfs)),
                        color = if (result.truePeakDbfs >= 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onToggleDetails) {
                Text(stringResource(if (expanded) R.string.audio_analysis_hide_details else R.string.audio_analysis_details))
            }
            if (expanded) {
                measurement?.let { result ->
                    MeasurementRow(stringResource(R.string.audio_analysis_peak),
                        if (result.truePeakDbfs.isFinite()) stringResource(R.string.audio_analysis_dbtp, result.truePeakDbfs)
                        else stringResource(R.string.audio_analysis_peak_missing))
                }
                Text("${track.fileType.orEmpty().uppercase()} · ${track.audioSampleRate} Hz · ${track.audioBitsDepth} bit",
                    style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.audio_analysis_method), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Text(stringResource(R.string.audio_analysis_fidelity), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.audio_analysis_fidelity_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onVerify) { Text(stringResource(R.string.audio_analysis_verify)) }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.audio_analysis_close))
            }
        }
    }
}

internal fun peakDescription(peakDb: Double): Int = when {
    !peakDb.isFinite() -> R.string.audio_analysis_peak_missing
    peakDb >= 0 -> R.string.audio_analysis_peak_warning
    else -> R.string.audio_analysis_headroom
}

@Composable
private fun MeasurementRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}
