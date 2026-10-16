package apincer.android.mmate.pro

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import apincer.android.mmate.R
import kotlinx.coroutines.flow.StateFlow

/** What MusicMate Pro adds (D2). Everything else is free. */
enum class ProFeature(@StringRes val title: Int) {
    QUALITY_REPORT(R.string.pro_feature_quality_report),
    BATCH_TAGS(R.string.pro_feature_batch_tags),
    MUSICBRAINZ(R.string.pro_feature_musicbrainz),
    ORGANIZE(R.string.pro_feature_organize)
}

/**
 * Whether Pro is unlocked, and how to buy it. The Play build sells it through Google Play; the GitHub
 * build includes every Pro feature (the app is open source, D3).
 */
interface ProStore {
    val isPro: StateFlow<Boolean>

    /** The localized price from Google Play, or null until it is known (always null in the GitHub build). */
    val price: StateFlow<String?>

    /** Starts the Google Play purchase; false when Google Play cannot sell it right now. */
    fun purchase(activity: Activity): Boolean

    /** Asks Google Play again which purchases the user owns, for example after a reinstall. */
    fun restore()

    /** True in the Play build, where Pro is bought; false where it is included. */
    val sellsPro: Boolean
}

object Pro {
    @Volatile
    private var store: ProStore? = null

    @JvmStatic
    fun store(context: Context): ProStore =
        store ?: synchronized(this) {
            store ?: ProStoreFactory.create(context.applicationContext).also { store = it }
        }

    /** Pro features unlocked; a debug build may force the answer for testing (see ProOverride). */
    @JvmStatic
    fun isPro(context: Context): Boolean =
        ProOverride.forced(context) ?: store(context).isPro.value
}
