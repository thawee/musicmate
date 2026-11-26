package apincer.android.mmate.ui

import androidx.appcompat.app.AlertDialog
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ScrollView
import android.view.View
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the compact verifier against the clipped RMS column it replaced. */
class LosslessLevelsLayoutTest {
    @Test
    fun sampledLevelsFitCompactWidthAndLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (scale in listOf(1f, 1.5f)) {
                lateinit var dialog: AlertDialog
                lateinit var scroll: ScrollView
                lateinit var action: View
                lateinit var rms: TextView
                lateinit var peak: TextView
                scenario.onActivity { activity ->
                    val config = Configuration(activity.resources.configuration).apply { fontScale = scale }
                    val context = ContextThemeWrapper(activity, R.style.AdaptiveTheme_MusicMateApp).apply {
                        applyOverrideConfiguration(config)
                    }
                    val content = LayoutInflater.from(context).inflate(R.layout.view_action_spectrum, null)
                    TagsActivity.adaptVerificationLayout(content)
                    scroll = content.findViewById(R.id.verification_scroll)
                    action = content.findViewById(R.id.button_ok)
                    content.findViewById<TextView>(R.id.filename).text = "Reference recording.flac"
                    content.findViewById<TextView>(R.id.format_labels).text = "FORMAT\nType:\nSample Rate:\nBit Depth:\nSize:"
                    content.findViewById<TextView>(R.id.format_values).text = "\nFLAC\n96 kHz\n24 bits\n45.2 MB"
                    content.findViewById<TextView>(R.id.analytics_labels).text = "ANALYTICS\nNyquist:\nCutoff:\nRange:"
                    content.findViewById<TextView>(R.id.analytics_values).text = "\n48 kHz\nNone found\nDR 14"
                    content.findViewById<TextView>(R.id.quality_score).text = "No sign of upsampling"
                    rms = content.findViewById(R.id.sampled_rms_value)
                    peak = content.findViewById(R.id.sampled_peak_value)
                    rms.text = "−121.6"
                    peak.text = "−2.0"
                    dialog = MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme).setView(content).create()
                    dialog.show()
                    dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.window!!.setLayout((320 * activity.resources.displayMetrics.density).toInt(), -2)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    for (value in listOf(rms, peak)) {
                        assertTrue("Value should be laid out", value.width > 0)
                        assertEquals("Level must stay on one line at font scale $scale", 1, value.lineCount)
                        assertEquals(0, value.layout.getEllipsisCount(0))
                        assertTrue(value.layout.getLineWidth(0) <= value.width - value.paddingLeft - value.paddingRight)
                    }
                    assertEquals(peak.top, rms.top)
                }
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                File(instrumentation.targetContext.getExternalFilesDir(null), "lossless-levels-$scale.png")
                    .outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                scenario.onActivity {
                    scroll.isSmoothScrollingEnabled = false
                    scroll.fullScroll(View.FOCUS_DOWN)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val visible = Rect()
                    assertTrue("Bottom action must remain reachable", action.getGlobalVisibleRect(visible))
                    assertEquals(action.height, visible.height())
                    dialog.dismiss()
                }
            }
        }
    }
}
