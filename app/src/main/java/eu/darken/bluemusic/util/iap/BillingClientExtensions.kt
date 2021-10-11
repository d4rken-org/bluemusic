package eu.darken.bluemusic.util.iap

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal val BillingResult.isSuccess: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.OK


fun <T : Any> Observable<T>.retryDelayed(
        maxRetryCount: Long = -1,
        delayFactor: Long = 1000,
        retryCondition: ((error: Throwable) -> Boolean)? = null
): Observable<T> {
    return this.retryWhen { errors ->
        val counter = AtomicLong()
        errors
                .takeWhile { error ->
                    val counterOk = maxRetryCount == -1L || counter.getAndIncrement() != maxRetryCount
                    val conditionOkay = retryCondition?.invoke(error) ?: true

                    if (counterOk && conditionOkay) return@takeWhile true
                    else throw error
                }
                .flatMap {
                    Timber.w("Retry count ${counter.get()}. Reason: %s", it.toString())
                    val actualCounter = if (maxRetryCount == -1L) 1 else counter.get()
                    Observable.timer(actualCounter * delayFactor, TimeUnit.MILLISECONDS)
                }
    }
}

fun <T : Any> Single<T>.retryDelayed(
        maxRetryCount: Long,
        delayFactor: Long,
        retryCondition: ((error: Throwable) -> Boolean)? = null
): Single<T> {
    return this.retryWhen { errors ->
        val counter = AtomicLong()
        errors
                .takeWhile { error ->
                    val counterOk = maxRetryCount == -1L || counter.getAndIncrement() != maxRetryCount
                    val conditionOkay = retryCondition?.invoke(error) ?: true

                    if (counterOk && conditionOkay) return@takeWhile true
                    else throw error
                }
                .flatMap {
                    Timber.w("Retry count ${counter.get()}. Reason: %s", it.toString())
                    val actualCounter = if (maxRetryCount == -1L) 1 else counter.get()
                    Flowable.timer(actualCounter * delayFactor, TimeUnit.MILLISECONDS)
                }
    }
}
