package eu.darken.bluemusic.util.iap

import com.android.billingclient.api.ProductDetails

data class Sku(
        val id: String
) {
    data class Details(
            val sku: Sku,
            val details: List<ProductDetails>,
    )
}