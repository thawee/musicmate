package apincer.android.mmate.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import apincer.android.mmate.R
import apincer.android.mmate.ui.compose.AboutScreen
import apincer.android.mmate.ui.compose.MusicMateTheme
import apincer.android.mmate.ui.compose.PieEntry
import apincer.music.core.Constants
import apincer.music.core.repository.TagRepository
import apincer.music.core.utils.ApplicationUtils
import apincer.music.core.utils.MusicMateExecutors
import apincer.music.core.utils.TagUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AboutActivity : ComponentActivity() {

    @Inject
    lateinit var tagRepos: TagRepository

    private val pieEntries = mutableStateListOf<PieEntry>()

    companion object {
        @JvmStatic
        fun showAbout(activity: Activity) {
            val intent = Intent(activity, AboutActivity::class.java)
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val appVersion = ApplicationUtils.getVersionNumber(this)

        setContent {
            MusicMateTheme {
                AboutScreen(
                    appVersion = appVersion,
                    pieEntries = pieEntries,
                    storageStatusText = "",
                    onBackClick = { finish() }
                )
            }
        }

        loadQualityStats()
    }

    private fun loadQualityStats() {
        MusicMateExecutors.execute {
            val encList = mutableMapOf<String, Int>()
            tagRepos.processAllMusics { tag ->
                val enc = TagUtils.getEncodingTypeShort(tag)
                encList[enc] = (encList[enc] ?: 0) + 1
            }

            val mappedColors = mapOf(
                Constants.LEGEND_MQA to ContextCompat.getColor(this, R.color.quality_mqa_background),
                Constants.LEGEND_DSD to ContextCompat.getColor(this, R.color.quality_dsd_background),
                Constants.LEGEND_HIRES to ContextCompat.getColor(this, R.color.quality_hr_background),
                Constants.LEGEND_CD to ContextCompat.getColor(this, R.color.quality_cd_background),
                Constants.LEGEND_STUDIO to ContextCompat.getColor(this, R.color.quality_cd_ext_background),
                Constants.LEGEND_LOSSY to ContextCompat.getColor(this, R.color.quality_lc_background)
            )

            val entries = encList.map { (enc, count) ->
                PieEntry(enc, count.toFloat(), mappedColors[enc] ?: Color.GRAY)
            }

            runOnUiThread {
                pieEntries.clear()
                pieEntries.addAll(entries)
            }
        }
    }
}
