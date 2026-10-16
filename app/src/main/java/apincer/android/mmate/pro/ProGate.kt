package apincer.android.mmate.pro

import android.app.Activity
import android.widget.Toast
import apincer.android.mmate.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The one check in front of every Pro feature. Pro: runs the action. Otherwise: explains Pro, with
 * Buy and Restore. The feature itself stays visible, so free users find out what Pro does.
 */
object ProGate {

    /** Runs [action] when Pro is unlocked; otherwise shows the Pro dialog for [feature]. */
    @JvmStatic
    fun require(activity: Activity, feature: ProFeature, action: Runnable) {
        if (Pro.isPro(activity)) action.run() else show(activity, feature)
    }

    /** True when Pro is unlocked; otherwise shows the Pro dialog for [feature] and returns false. */
    @JvmStatic
    fun check(activity: Activity, feature: ProFeature): Boolean {
        if (Pro.isPro(activity)) return true
        show(activity, feature)
        return false
    }

    /** The Pro dialog; [feature] names what the user just tried, or null from the About screen. */
    @JvmStatic
    fun show(activity: Activity, feature: ProFeature?) {
        val store = Pro.store(activity)
        val intro = feature?.let { activity.getString(R.string.pro_needs, activity.getString(it.title)) + "\n\n" } ?: ""
        val price = store.price.value
        MaterialAlertDialogBuilder(activity, R.style.AlertDialogTheme)
            .setTitle(R.string.pro_title)
            .setMessage(intro + activity.getString(R.string.pro_includes))
            .setPositiveButton(if (price != null) activity.getString(R.string.pro_buy_price, price) else activity.getString(R.string.pro_buy)) { _, _ ->
                if (!store.purchase(activity)) {
                    Toast.makeText(activity, R.string.pro_unavailable, Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton(R.string.pro_restore) { _, _ ->
                store.restore()
                Toast.makeText(activity, R.string.pro_checking, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.pro_not_now, null)
            .show()
    }
}
