package apincer.android.mmate.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import apincer.music.core.model.Track
import apincer.android.mmate.ui.compose.QualityBadge
import apincer.android.mmate.R

class QualityIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var track by mutableStateOf<Track?>(null)
    private var labelStr by mutableStateOf<String?>(null)

    init {
        attrs?.let {
            val typedArray = context.theme.obtainStyledAttributes(it, R.styleable.QualityIndicatorView, 0, 0)
            try {
                val lbl = typedArray.getString(R.styleable.QualityIndicatorView_label)
                if (lbl != null) {
                    labelStr = lbl
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    @Composable
    override fun Content() {
        if (track != null) {
            QualityBadge(track = track)
        } else {
            QualityBadge(labelStr = labelStr)
        }
    }

    fun setMusicItem(tag: Track?) {
        this.track = tag
    }

    fun setLabel(label: String?) {
        this.labelStr = label
    }
}
