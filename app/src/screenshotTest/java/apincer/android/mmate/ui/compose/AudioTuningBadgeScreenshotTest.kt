package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import apincer.android.mmate.audio.dsp.SoundMode
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Tuning badge guard", widthDp = 360, heightDp = 260, showBackground = true)
@Composable
fun AudioTuningBadgeGuardPreview() {
    MusicMateTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AudioTuningBadge(SoundMode.REFERENCE, enabled = true, unavailable = false, nightListening = false,
                profileName = "HD 600", crossfeed = true) {}
            AudioTuningBadge(SoundMode.REFERENCE, enabled = true, unavailable = false, nightListening = true,
                profileName = "HD 600", crossfeed = true, guard = GuardLight.LIMITING) {}
            AudioTuningBadge(SoundMode.REFERENCE, enabled = true, unavailable = false, nightListening = false,
                profileName = "A very long headphone profile name", guard = GuardLight.CLIPPING) {}
        }
    }
}
