package apincer.android.mmate.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProEntitlementTest {
    private class MemoryCache(var value: Boolean = false) : EntitlementCache {
        override fun read() = value
        override fun write(pro: Boolean) {
            value = pro
        }
    }

    private val acknowledged = mutableListOf<String>()

    private fun entitlement(cached: Boolean = false) =
        MemoryCache(cached).let { it to ProEntitlement(it) { token -> acknowledged += token } }

    private fun purchase(state: OwnedState, acknowledged: Boolean = true, product: String = PRO_PRODUCT_ID, token: String = "t1") =
        OwnedPurchase(listOf(product), state, acknowledged, token)

    @Test
    fun aPurchasedProductUnlocksProAndIsRemembered() {
        val (cache, pro) = entitlement()
        pro.onQuery(OwnedQuery.Success(listOf(purchase(OwnedState.PURCHASED))))
        assertTrue(pro.isPro.value)
        assertTrue(cache.value)
    }

    @Test
    fun aPendingPurchaseDoesNotUnlockPro() {
        val (_, pro) = entitlement()
        pro.onPurchasesUpdated(listOf(purchase(OwnedState.PENDING, acknowledged = false)))
        assertFalse(pro.isPro.value)
        assertTrue("A pending purchase is not acknowledged", acknowledged.isEmpty())
    }

    @Test
    fun unacknowledgedPurchasesAreAcknowledgedOnEveryQuery() {
        val (_, pro) = entitlement()
        pro.onQuery(OwnedQuery.Success(listOf(purchase(OwnedState.PURCHASED, acknowledged = false, token = "abc"))))
        pro.onQuery(OwnedQuery.Success(listOf(purchase(OwnedState.PURCHASED, acknowledged = false, token = "abc"))))
        assertEquals(listOf("abc", "abc"), acknowledged)
        pro.onQuery(OwnedQuery.Success(listOf(purchase(OwnedState.PURCHASED, acknowledged = true, token = "abc"))))
        assertEquals("An acknowledged purchase is left alone", 2, acknowledged.size)
    }

    @Test
    fun aFailedQueryKeepsProForAnOfflineBuyer() {
        val (cache, pro) = entitlement(cached = true)
        assertTrue("Starts from the cached answer", pro.isPro.value)
        pro.onQuery(OwnedQuery.Failure(responseCode = 2)) // SERVICE_UNAVAILABLE
        pro.onQuery(OwnedQuery.Failure(responseCode = 3)) // BILLING_UNAVAILABLE
        assertTrue(pro.isPro.value)
        assertTrue(cache.value)
    }

    @Test
    fun aSuccessfulQueryWithoutTheProductRevokesProAfterARefund() {
        val (cache, pro) = entitlement(cached = true)
        pro.onQuery(OwnedQuery.Success(emptyList()))
        assertFalse(pro.isPro.value)
        assertFalse(cache.value)
    }

    @Test
    fun purchaseUpdatesNeverRevoke() {
        val (_, pro) = entitlement(cached = true)
        pro.onPurchasesUpdated(emptyList())
        assertTrue(pro.isPro.value)
    }

    @Test
    fun otherProductsDoNotUnlockPro() {
        val (_, pro) = entitlement()
        pro.onQuery(OwnedQuery.Success(listOf(purchase(OwnedState.PURCHASED, product = "something_else"))))
        assertFalse(pro.isPro.value)
    }
}
