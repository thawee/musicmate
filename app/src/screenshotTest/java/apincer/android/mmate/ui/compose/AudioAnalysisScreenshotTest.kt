package apincer.android.mmate.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import apincer.music.core.model.AudioTag
import apincer.music.core.playback.Loudness
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Summary", widthDp = 360, heightDp = 740)
@Composable
fun AudioAnalysisSummaryPreview() = AnalysisPreview(false, false, Loudness(-18.3, -1.97, 225.0))

@PreviewTest
@Preview(name = "Details large text", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
fun AudioAnalysisDetailsPreview() = AnalysisPreview(true, false, Loudness(-8.3, 0.4, 225.0))

@PreviewTest
@Preview(name = "Measuring", widthDp = 360, heightDp = 740)
@Composable
fun AudioAnalysisMeasuringPreview() = AnalysisPreview(false, true, null)

@PreviewTest
@Preview(name = "Unavailable", widthDp = 360, heightDp = 740)
@Composable
fun AudioAnalysisUnavailablePreview() = AnalysisPreview(false, false, null)

@Composable
private fun AnalysisPreview(expanded: Boolean, measuring: Boolean, loudness: Loudness?) {
    val track = AudioTag().apply {
        title = "Take On Me"
        artist = "a-ha"
        fileType = "flac"
        audioSampleRate = 44100
        audioBitsDepth = 16
        drScore = if (loudness != null) 14.0 else 0.0
    }
    MusicMateTheme {
        AudioAnalysisReport(track, loudness, measuring, expanded, {}, {}, {}, {})
    }
}
