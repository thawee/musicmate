package apincer.android.mmate.pro

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** GitHub build: every Pro feature is included; there is nothing to buy and no billing library. */
object ProStoreFactory {
    fun create(context: Context): ProStore = IncludedPro
}

private object IncludedPro : ProStore {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
    override val price: StateFlow<String?> = MutableStateFlow(null)
    override fun purchase(activity: Activity): Boolean = false
    override fun restore() = Unit
    override val sellsPro: Boolean = false
}
