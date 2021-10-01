package eu.darken.bluemusic.util.iap

import com.android.billingclient.api.BillingResult

class BillingClientException(val result: BillingResult?) : Exception() {
    override val message: String?
        get() = result?.debugMessage

    override fun toString(): String = "BillingClientException(code=${result?.responseCode}, message=${result?.debugMessage})"
}