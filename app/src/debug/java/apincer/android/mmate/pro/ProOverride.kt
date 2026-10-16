package apincer.android.mmate.pro

import android.content.Context
import java.io.File

/**
 * Debug builds only: forces Pro on or off for testing the gates without a purchase. Write "pro" or
 * "free" to files/pro_override (`adb shell run-as <package> sh -c 'echo free > files/pro_override'`);
 * remove the file to use the real entitlement. Release builds have a version that always returns null.
 */
object ProOverride {
    fun forced(context: Context): Boolean? =
        when (runCatching { File(context.filesDir, "pro_override").readText().trim() }.getOrNull()) {
            "pro" -> true
            "free" -> false
            else -> null
        }
}
