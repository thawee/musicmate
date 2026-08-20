package apincer.android.mmate.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import apincer.music.core.model.Track
import apincer.android.mmate.ui.compose.NewBadge

class NewIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var track by mutableStateOf<Track?>(null)

    @Composable
    override fun Content() {
        NewBadge(track = track)
    }

    fun setMusicItem(tag: Track?) {
        this.track = tag
    }
}
