package apincer.android.mmate.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import apincer.music.core.model.Track
import apincer.android.mmate.ui.compose.RatingBadge
import apincer.android.mmate.R

class RatingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var track by mutableStateOf<Track?>(null)
    private var mode by mutableStateOf<String?>(null)

    init {
        attrs?.let {
            val typedArray = context.theme.obtainStyledAttributes(it, R.styleable.RatingIndicatorView, 0, 0)
            try {
                val m = typedArray.getString(R.styleable.RatingIndicatorView_mode)
                if (m != null) {
                    mode = m
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    @Composable
    override fun Content() {
        RatingBadge(track = track, mode = mode)
    }

    fun setMusicItem(tag: Track?) {
        this.track = tag
    }
}
