package eu.darken.bluemusic.util.iap

import com.android.billingclient.api.Purchase

data class PurchasedSku(val sku: Sku, val purchase: Purchase) {
    override fun toString(): String = "IAP(sku=$sku, purchase=${purchase.skus})"
}