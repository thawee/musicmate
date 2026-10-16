package apincer.android.mmate.pro

import android.content.Context

/** Release builds never override the Pro entitlement. */
object ProOverride {
    @Suppress("UNUSED_PARAMETER")
    fun forced(context: Context): Boolean? = null
}
