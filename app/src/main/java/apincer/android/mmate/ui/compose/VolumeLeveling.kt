package apincer.android.mmate.ui.compose

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import apincer.android.mmate.R
import apincer.android.mmate.audio.OutputGuard
import apincer.music.core.Constants
import apincer.music.core.Settings
import apincer.music.core.playback.ReplayGainManager
import apincer.music.core.playback.ReplayGainManager.Leveling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

internal val LEVELING_KEYS = setOf(
    Constants.PREF_REPLAYGAIN_MODE,
    Constants.PREF_REPLAYGAIN_PREAMP,
    Constants.PREF_REPLAYGAIN_PREVENT_CLIPPING,
    Constants.PREF_REPLAYGAIN_TARGET,
    Constants.PREF_REPLAYGAIN_TAGS_CHANGED,
    Constants.PREF_REPLAYGAIN_UNTAGGED_POLICY,
)

internal val LEVELING_MODES = listOf("off", "track", "album")
internal val LEVELING_PREAMPS = listOf(-3f, 0f, 3f)
internal val LEVELING_TARGETS = listOf(-14f, -18f)

/** A signed dB number with a real minus sign, as in "−3" or "+3". */
internal fun signedDb(db: Double, decimals: Int = 1): String {
    val magnitude = String.format(Locale.getDefault(), "%.${decimals}f", abs(db))
    return when {
        db <= -0.05 -> "−$magnitude"
        db >= 0.05 -> "+$magnitude"
        else -> "0"
    }
}

/** What leveling does to [path] now, recomputed off the main thread when a leveling setting changes. */
@Composable
internal fun rememberLeveling(path: String?): Leveling? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current.applicationContext
    var settingsVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in LEVELING_KEYS) settingsVersion++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val leveling by produceState<Leveling?>(null, path, settingsVersion) {
        value = null
        value = path?.let { withContext(Dispatchers.IO) { ReplayGainManager.getInstance().leveling(context, it) } }
    }
    return leveling
}

internal data class LevelingStatus(val text: String, val description: String, val color: Color)

@Composable
internal fun levelingStatus(leveling: Leveling?, remoteTarget: String?): LevelingStatus? {
    leveling ?: return null
    val muted = MusicMateDesignTokens.Text.Muted
    fun plain(text: String) = LevelingStatus(text, text, muted)
    return when {
        leveling.kind == Leveling.Kind.OFF -> plain(stringResource(R.string.leveling_off))
        remoteTarget != null -> plain(stringResource(R.string.leveling_remote, remoteTarget))
        leveling.kind == Leveling.Kind.NO_TAGS && leveling.appliedDb() > -0.05 -> plain(stringResource(R.string.leveling_no_tags))
        else -> {
            val db = leveling.appliedDb()
            val text = stringResource(when {
                leveling.kind == Leveling.Kind.NO_TAGS -> R.string.leveling_no_tags_preamp
                leveling.album -> R.string.leveling_album_applied
                else -> R.string.leveling_applied
            }, signedDb(db))
            val description = when {
                db <= -0.05 -> stringResource(R.string.leveling_desc_lowered, String.format(Locale.getDefault(), "%.1f", -db))
                db >= 0.05 -> stringResource(R.string.leveling_desc_raised, String.format(Locale.getDefault(), "%.1f", db))
                else -> stringResource(R.string.leveling_desc_unchanged)
            }
            LevelingStatus(text, description, MusicMateDesignTokens.Brand.AcousticTeal)
        }
    }
}

/** Leveling mode and pre-amp for the quick tuning sheet, with what they do to the playing track. */
@Composable
internal fun VolumeLevelingControl(
    remoteTarget: String?,
    trackPath: String? = MainScaffoldState.get().nowPlayingState.track.value?.path
) {
    val context = LocalContext.current.applicationContext
    var mode by remember { mutableStateOf(Settings.getReplayGainMode(context).lowercase(Locale.ROOT)) }
    var preamp by remember { mutableStateOf(Settings.getReplayGainPreamp(context)) }
    val status = levelingStatus(rememberLeveling(trackPath), remoteTarget)
    val guard = outputGuardLight(local = remoteTarget == null)
    val guardState by OutputGuard.state.collectAsState()
    val modeLabels = mapOf(
        "off" to stringResource(R.string.leveling_mode_off),
        "track" to stringResource(R.string.leveling_mode_track),
        "album" to stringResource(R.string.leveling_mode_album),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TuningLabel(stringResource(R.string.leveling_title).uppercase(Locale.getDefault()))
        FlowRow(Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LEVELING_MODES.forEach { option ->
                TuningChoiceChip(modeLabels.getValue(option), mode == option) {
                    mode = option
                    Settings.setReplayGainMode(context, option)
                }
            }
        }
        TuningHint(stringResource(when (mode) {
            "off" -> R.string.leveling_mode_off_hint
            "album" -> R.string.leveling_mode_album_hint
            else -> R.string.leveling_mode_track_hint
        }))
        if (mode != "off") {
            Text(stringResource(R.string.leveling_preamp), color = MusicMateDesignTokens.Text.Primary, fontSize = 14.sp)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LEVELING_PREAMPS.forEach { db ->
                    TuningChoiceChip(stringResource(R.string.leveling_preamp_value, signedDb(db.toDouble(), 0)),
                        abs(preamp - db) < 0.1f) {
                        preamp = db
                        Settings.setReplayGainPreamp(context, db)
                    }
                }
            }
        }
        when {
            remoteTarget != null && mode != "off" -> TuningHint(stringResource(R.string.leveling_not_applied))
            status != null && mode != "off" -> Text(stringResource(R.string.leveling_now_playing, status.text),
                color = status.color, fontSize = 13.sp,
                modifier = Modifier.semantics { contentDescription = status.description })
        }
        if (guard != null) {
            Text(stringResource(if (guard == GuardLight.CLIPPING) R.string.leveling_output_clipping
                else R.string.leveling_output_limiting, guardState.reductionDb),
                color = guard.color, fontSize = 13.sp)
        }
    }
}
