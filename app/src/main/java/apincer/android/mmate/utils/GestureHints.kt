package apincer.android.mmate.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R

/**
 * Gesture discovery hints for onboarding users to key interactions.
 * Shows contextual tips once per user (stored in SharedPreferences).
 */
object GestureHints {

    private const val PREFS_NAME = "gesture_hints"
    private const val KEY_TRACK_TAP = "hint_track_tap_shown"
    private const val KEY_ART_TAP = "hint_art_tap_shown"
    private const val KEY_LONG_PRESS = "hint_long_press_shown"
    private const val KEY_SWIPE_QUEUE = "hint_swipe_queue_shown"
    private const val KEY_DRAG_REORDER = "hint_drag_reorder_shown"
    private const val KEY_DOUBLE_TAP_FLIP = "hint_double_tap_flip_shown"

    enum class HintType(
        val key: String,
        val title: String,
        val message: String,
        val iconRes: Int
    ) {
        TRACK_TAP(KEY_TRACK_TAP, "Tap to Play", "Tap any song to play it instantly", R.drawable.ic_baseline_play_arrow_24),
        ART_TAP(KEY_ART_TAP, "Tap Artwork", "Tap album artwork for quick play", R.drawable.ic_album_black_24dp),
        LONG_PRESS(KEY_LONG_PRESS, "Long Press", "Long-press a song for batch editing", R.drawable.ic_baseline_edit_note_24),
        SWIPE_QUEUE(KEY_SWIPE_QUEUE, "Swipe to Remove", "Swipe queue items left/right to remove", R.drawable.rounded_delete_24),
        DRAG_REORDER(KEY_DRAG_REORDER, "Drag to Reorder", "Drag the handle to reorder queue", R.drawable.rounded_drag_indicator_24),
        DOUBLE_TAP_FLIP(KEY_DOUBLE_TAP_FLIP, "Double Tap Artwork", "Double-tap Now Playing artwork for audio specs", R.drawable.ic_baseline_playlist_play_24);

        companion object {
            fun fromKey(key: String): HintType? = values().firstOrNull { it.key == key }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasShown(context: Context, hint: HintType): Boolean =
        getPrefs(context).getBoolean(hint.key, false)

    fun markShown(context: Context, hint: HintType) {
        getPrefs(context).edit().putBoolean(hint.key, true).apply()
    }

    fun resetAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    @Composable
    fun GestureHintCard(
        hint: HintType,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val showHint = remember { mutableStateOf(!hasShown(context, hint)) }

        if (showHint.value) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E2E)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFB300))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = hint.iconRes),
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = hint.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = hint.message,
                            color = Color(0xFFBDBDBD),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = {
                        markShown(context, hint)
                        showHint.value = false
                        onDismiss()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.round_close_24),
                            contentDescription = "Dismiss",
                            tint = Color(0xFF9E9E9E)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun GestureHintBanner(
        hints: List<HintType> = listOf(HintType.TRACK_TAP, HintType.ART_TAP, HintType.LONG_PRESS),
        onDismissAll: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val visibleHints = hints.filter { !hasShown(context, it) }

        if (visibleHints.isNotEmpty()) {
            Column(
                modifier = modifier.fillMaxWidth()
            ) {
                visibleHints.forEach { hint ->
                    GestureHintCard(hint = hint, onDismiss = {})
                }
                TextButton(
                    onClick = {
                        visibleHints.forEach { markShown(context, it) }
                        onDismissAll()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Don't show these tips again",
                        color = Color(0xFF9E9E9E),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}