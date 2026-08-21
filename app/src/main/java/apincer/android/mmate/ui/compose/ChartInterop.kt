package apincer.android.mmate.ui.compose

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import apincer.music.core.model.Track

object ChartInterop {
    @JvmStatic
    fun setQualityPieChartContent(view: ComposeView, entries: List<PieEntry>) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            MusicMateTheme {
                QualityPieChart(entries = entries)
            }
        }
    }

    @JvmStatic
    fun setDynamicRangeMeterContent(view: ComposeView, track: Track?) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            MusicMateTheme {
                DynamicRangeMeter(track = track)
            }
        }
    }
}
