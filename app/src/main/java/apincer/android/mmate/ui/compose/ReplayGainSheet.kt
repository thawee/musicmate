package apincer.android.mmate.ui.compose

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import apincer.android.mmate.MusixMateApp
import apincer.android.mmate.R
import apincer.music.core.Settings
import apincer.music.core.model.Track
import apincer.music.core.playback.ReplayGainManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ReplayGainTags(val loaded: Boolean = false,
    val info: ReplayGainManager.ReplayGainInfo? = null) {
    val track get() = info?.let { it.hasTrackGain && it.trackGainDb.isFinite() } == true
    val album get() = info?.let { it.hasAlbumGain && it.albumGainDb.isFinite() } == true
}

@Composable
internal fun rememberReplayGainTags(path: String?): ReplayGainTags {
    val context = LocalContext.current.applicationContext
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in LEVELING_KEYS) version++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return key(path, version) {
        val tags by produceState(ReplayGainTags(), path, version) {
            value = withContext(Dispatchers.IO) {
                ReplayGainTags(true, path?.let { ReplayGainManager.getInstance().getReplayGain(it) })
            }
        }
        tags
    }
}

@Composable
internal fun LevelingStatusButton(track: Track?, remoteTarget: String?, modifier: Modifier = Modifier, missingOnly: Boolean = false) {
    if (track == null) return
    var open by remember(track.path) { mutableStateOf(false) }
    val tags = rememberReplayGainTags(track.path)
    if (missingOnly && (!tags.loaded || tags.track)) return
    val leveling = rememberLeveling(track.path)
    val status = levelingStatus(leveling, remoteTarget)
    val label = when {
        !tags.loaded -> stringResource(R.string.replaygain_title)
        !tags.track && !tags.album -> stringResource(R.string.replaygain_missing_short)
        leveling?.kind == ReplayGainManager.Leveling.Kind.OFF -> stringResource(R.string.replaygain_ready_off)
        else -> status?.text ?: stringResource(R.string.replaygain_title)
    }
    val openLabel = stringResource(R.string.replaygain_open)
    TextButton(onClick = { open = true }, modifier = modifier.heightIn(min = 48.dp)
        .semantics { contentDescription = openLabel; stateDescription = label },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MusicMateDesignTokens.Brand.AcousticTeal)) {
        Text(label, modifier = Modifier.weight(1f, fill = false), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(18.dp))
    }
    if (open) ReplayGainSheet(track, remoteTarget) { open = false }
}

@Composable
internal fun ReplayGainSheet(track: Track, remoteTarget: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val measurement = remember { ReplayGainMeasurement(context, MusixMateApp.getInstance().tagRepository) }
    DisposableEffect(measurement) { onDispose { measurement.close() } }
    val state by measurement.state.collectAsState()
    val tags = rememberReplayGainTags(track.path)
    AudioTuningSheet(onDismissRequest = onDismiss, compact = true,
        title = stringResource(R.string.replaygain_title), closeLabel = stringResource(R.string.replaygain_close)) {
        ReplayGainSheetContent(track, remoteTarget, tags, state, measurement::measureTrack,
            { measurement.measureAlbum(track) }, measurement::scanMissing,
            measurement::measureMissing, measurement::cancel, measurement::retryFailed)
    }
}

@Composable
internal fun ReplayGainSheetContent(track: Track, remoteTarget: String?, tags: ReplayGainTags,
    state: ReplayGainMeasurement.State, onMeasureTrack: (Track) -> Unit, onMeasureAlbum: () -> Unit,
    onScan: () -> Unit, onMeasureMissing: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val busy = state.phase == ReplayGainMeasurement.Phase.SCANNING || state.phase == ReplayGainMeasurement.Phase.MEASURING
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title.orEmpty().ifBlank { java.io.File(track.path).name },
                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MusicMateDesignTokens.Text.Primary)
            if (!track.artist.isNullOrBlank()) TuningHint(track.artist)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MusicMateDesignTokens.Surfaces.Surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.replaygain_tags), fontSize = 12.sp, color = MusicMateDesignTokens.Text.Secondary)
                Text(stringResource(when {
                    !tags.loaded -> R.string.replaygain_reading
                    tags.track -> R.string.replaygain_ready
                    tags.album -> R.string.replaygain_album_only
                    else -> R.string.replaygain_missing
                }), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    color = if (tags.track || tags.album) MusicMateDesignTokens.Brand.AcousticTeal else MusicMateDesignTokens.Text.Primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GainValue(stringResource(R.string.replaygain_track_gain), if (tags.track) tags.info?.trackGainDb else null)
                    GainValue(stringResource(R.string.replaygain_album_gain), if (tags.album) tags.info?.albumGainDb else null)
                }
            }
        }
        if (busy) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (state.phase == ReplayGainMeasurement.Phase.SCANNING)
                    R.string.replaygain_scan else R.string.measure_loudness_title), fontWeight = FontWeight.Medium)
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(),
                    color = MusicMateDesignTokens.Brand.AcousticTeal)
                Text(stringResource(R.string.replaygain_progress,
                    (state.progress * state.total).toInt(), state.total), color = MusicMateDesignTokens.Text.Secondary)
                state.currentTrack?.let { TuningHint(it.title.orEmpty()) }
                TextButton(onClick = onCancel, enabled = !state.cancelRequested) { Text(stringResource(R.string.replaygain_cancel)) }
                TuningHint(stringResource(R.string.replaygain_cancel_hint))
            }
        } else {
            if (state.error != null) {
                TuningHint(stringResource(R.string.replaygain_error))
                if (state.failedTracks.isEmpty()) TextButton(onClick = if (state.phase == ReplayGainMeasurement.Phase.IDLE) onScan else onRetry) { Text(stringResource(R.string.replaygain_retry)) }
            }
            if (state.phase == ReplayGainMeasurement.Phase.COMPLETE || state.phase == ReplayGainMeasurement.Phase.CANCELLED) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(if (state.phase == ReplayGainMeasurement.Phase.CANCELLED)
                        R.string.replaygain_cancelled else R.string.replaygain_done), fontWeight = FontWeight.Medium)
                    TuningHint(stringResource(R.string.replaygain_result, state.succeeded, state.failedTracks.size))
                    if (state.failedTracks.isNotEmpty()) TextButton(onClick = onRetry) { Text(stringResource(R.string.replaygain_retry)) }
                }
            }
            Button(onClick = { onMeasureTrack(track) }, enabled = tags.loaded, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MusicMateDesignTokens.Brand.AcousticTeal,
                    contentColor = MusicMateDesignTokens.Surfaces.Canvas)) {
                Text(stringResource(if (tags.track) R.string.replaygain_remeasure_track else R.string.replaygain_measure_track))
            }
            TuningHint(stringResource(R.string.replaygain_metadata_hint))
            if (!track.album.isNullOrBlank()) OutlinedButton(onClick = onMeasureAlbum, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.replaygain_measure_album))
            }
        }
        HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
        VolumeLevelingControl(remoteTarget, track.path)
        if (MainScaffoldState.get().nowPlayingState.track.value?.path != track.path)
            TuningHint(stringResource(R.string.replaygain_not_playing))
        HorizontalDivider(color = MusicMateDesignTokens.Surfaces.BorderSubtle)
        TuningDisclosure(stringResource(R.string.replaygain_library), stringResource(R.string.replaygain_library_hint)) {
            OutlinedButton(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.replaygain_scan))
            }
            var visibleMissing by remember { mutableIntStateOf(5) }
            if (state.libraryScanned) {
                Text(stringResource(if (state.missingTracks.isEmpty()) R.string.replaygain_no_missing
                    else R.string.replaygain_missing_count, state.missingTracks.size), fontWeight = FontWeight.Medium)
                state.missingTracks.take(visibleMissing).forEach { missing ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(missing.title.orEmpty().ifBlank { java.io.File(missing.path).name }, fontSize = 14.sp)
                        TuningHint(missing.artist.orEmpty())
                    }
                }
                if (state.missingTracks.size > visibleMissing) TextButton(onClick = { visibleMissing += 20 }) {
                    Text(stringResource(R.string.replaygain_show_more, state.missingTracks.size - visibleMissing))
                }
                if (state.missingTracks.isNotEmpty()) OutlinedButton(onClick = onMeasureMissing, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.replaygain_fix_missing)) }
            }
            var charging by remember { mutableStateOf(Settings.isMeasureLoudnessWhileCharging(context)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.replaygain_charging), fontSize = 14.sp)
                    TuningHint(stringResource(R.string.replaygain_charging_hint))
                }
                Switch(checked = charging, onCheckedChange = {
                    charging = it; Settings.setMeasureLoudnessWhileCharging(context, it)
                    apincer.android.mmate.worker.LoudnessWorker.schedule(context)
                }, modifier = Modifier.semantics { contentDescription = context.getString(R.string.replaygain_charging) })
            }
        }
    }
}

@Composable
private fun GainValue(label: String, gain: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MusicMateDesignTokens.Text.Secondary, fontSize = 12.sp)
        Text(gain?.let { "${signedDb(it)} dB" } ?: stringResource(R.string.replaygain_not_measured),
            fontSize = 17.sp, color = MusicMateDesignTokens.Text.Primary)
    }
}
