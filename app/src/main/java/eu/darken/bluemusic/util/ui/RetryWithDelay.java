package eu.darken.bluemusic.util.ui;

import org.reactivestreams.Publisher;

import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

public class RetryWithDelay implements Function<Flowable<? extends Throwable>, Publisher<?>> {
    private final int maxRetries;
    private final long delayMillis;
    private int retryCount = 0;

    public RetryWithDelay(final int maxRetries, final long delayMillis) {
        this.maxRetries = maxRetries;
        this.delayMillis = delayMillis;
    }

    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public Publisher<?> apply(final Flowable<? extends Throwable> attempts) {
        return attempts.flatMap((Function<Throwable, Publisher<?>>) throwable -> {
            if (++retryCount < maxRetries) return Flowable.timer(delayMillis, TimeUnit.MILLISECONDS);
            return Flowable.error(throwable);
        });
    }
}
