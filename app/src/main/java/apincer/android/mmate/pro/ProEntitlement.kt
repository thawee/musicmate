package apincer.android.mmate.pro

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The one-time Google Play product that unlocks Pro. */
const val PRO_PRODUCT_ID = "pro_unlock"

enum class OwnedState { PURCHASED, PENDING, OTHER }

/** A purchase as Google Play reports it, without the billing library's types. */
data class OwnedPurchase(
    val products: List<String>,
    val state: OwnedState,
    val acknowledged: Boolean,
    val token: String
)

/** The answer to "which purchases does the user own?". */
sealed interface OwnedQuery {
    data class Success(val purchases: List<OwnedPurchase>) : OwnedQuery
    data class Failure(val responseCode: Int) : OwnedQuery
}

/** Where the last known entitlement is kept, so Pro works offline. */
interface EntitlementCache {
    fun read(): Boolean
    fun write(pro: Boolean)
}

/**
 * Decides whether Pro is unlocked from what Google Play reports:
 * - only `pro_unlock` in PURCHASED counts; a PENDING purchase (cash or carrier payment) does not;
 * - every such purchase not yet acknowledged is acknowledged, since Play refunds unacknowledged
 *   purchases after three days;
 * - Pro is revoked only by a query that succeeded without the product (a refund); a failed query
 *   (offline, Play unavailable) keeps the cached answer, so buyers keep Pro offline.
 */
class ProEntitlement(
    private val cache: EntitlementCache,
    private val acknowledge: (String) -> Unit
) {
    private val pro = MutableStateFlow(cache.read())
    val isPro: StateFlow<Boolean> = pro

    /** The result of a full query of owned purchases. */
    fun onQuery(result: OwnedQuery) {
        when (result) {
            is OwnedQuery.Success -> apply(result.purchases, authoritative = true)
            is OwnedQuery.Failure -> Unit // keep the cached answer
        }
    }

    /** Purchases reported by a finished purchase flow; these can only grant, never revoke. */
    fun onPurchasesUpdated(purchases: List<OwnedPurchase>) {
        apply(purchases, authoritative = false)
    }

    private fun apply(purchases: List<OwnedPurchase>, authoritative: Boolean) {
        val owned = purchases.filter { PRO_PRODUCT_ID in it.products && it.state == OwnedState.PURCHASED }
        owned.filterNot { it.acknowledged }.forEach { acknowledge(it.token) }
        val granted = owned.isNotEmpty()
        if (granted || authoritative) {
            pro.value = granted
            cache.write(granted)
        }
    }
}
