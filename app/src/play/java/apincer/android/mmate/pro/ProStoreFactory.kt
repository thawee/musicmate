package apincer.android.mmate.pro

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Play build: Pro is the one-time Google Play product [PRO_PRODUCT_ID]. */
object ProStoreFactory {
    fun create(context: Context): ProStore = PlayProStore(context)
}

private class PlayProStore(context: Context) : ProStore {
    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val entitlement = ProEntitlement(
        cache = object : EntitlementCache {
            override fun read() = prefs.getBoolean(KEY_PRO, false)
            override fun write(pro: Boolean) {
                prefs.edit().putBoolean(KEY_PRO, pro).apply()
            }
        },
        acknowledge = ::acknowledge
    )
    override val isPro: StateFlow<Boolean> = entitlement.isPro
    private val formattedPrice = MutableStateFlow<String?>(null)
    override val price: StateFlow<String?> = formattedPrice
    override val sellsPro: Boolean = true

    @Volatile
    private var productDetails: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                entitlement.onPurchasesUpdated(purchases.map(::owned))
            } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                queryOwned()
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Also catches purchases completed while the app was closed, and restores after a reinstall
                    queryOwned()
                    queryProduct()
                } else {
                    Log.w(TAG, "Google Play Billing unavailable: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection reconnects on the next call
            }
        })
    }

    private fun queryOwned() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            entitlement.onQuery(
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    OwnedQuery.Success(purchases.map(::owned))
                } else {
                    OwnedQuery.Failure(result.responseCode)
                }
            )
        }
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val found = details.productDetailsList.firstOrNull { it.productId == PRO_PRODUCT_ID }
            productDetails = found
            formattedPrice.value = found?.let(::offer)?.formattedPrice
        }
    }

    private fun offer(details: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? =
        details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: details.oneTimePurchaseOfferDetails

    override fun purchase(activity: Activity): Boolean {
        val details = productDetails
        if (details == null) {
            queryProduct() // perhaps ready next time
            return false
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { offer(details)?.offerToken?.let(::setOfferToken) }
            .build()
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun restore() = queryOwned()

    private fun acknowledge(token: String) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // Acknowledged again on the next query
                Log.w(TAG, "Acknowledge failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun owned(purchase: Purchase) = OwnedPurchase(
        products = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> OwnedState.PURCHASED
            Purchase.PurchaseState.PENDING -> OwnedState.PENDING
            else -> OwnedState.OTHER
        },
        acknowledged = purchase.isAcknowledged,
        token = purchase.purchaseToken
    )

    private companion object {
        const val TAG = "PlayProStore"
        const val KEY_PRO = "pro_unlocked"
    }
}
