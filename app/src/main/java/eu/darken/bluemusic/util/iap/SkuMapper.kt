package eu.darken.bluemusic.util.iap

import com.android.billingclient.api.Purchase

object SkuMapper {

    fun Purchase.toPurchasedSku(): Collection<PurchasedSku> = products.map {
        PurchasedSku(Sku(it), this)
    }

    fun mapPurchases(purchases: Collection<Purchase>): Boolean = try {
        purchases
                .flatMap { it.toPurchasedSku() }
                .any { it.sku == AvailableSkus.UPGRADE_PREMIUM.sku }
    } catch (e: Exception) {
        false
    }
}