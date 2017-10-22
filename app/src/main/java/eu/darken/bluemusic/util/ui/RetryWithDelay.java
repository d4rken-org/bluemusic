package eu.darken.bluemusic.util.ui;

import org.reactivestreams.Publisher;

import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

public class RetryWithDelay implements Function<Flowable<? extends Throwable>, Publisher<?>> {
    private final int maxRetries;
    private final int delayMillis;
    private int retryCount = 0;

    public RetryWithDelay(final int maxRetries, final int delayMillis) {
        this.maxRetries = maxRetries;
        this.delayMillis = delayMillis;
    }

    @Override
    public Publisher<?> apply(final Flowable<? extends Throwable> attempts) {
        return attempts.flatMap((Function<Throwable, Publisher<?>>) throwable -> {
            if (++retryCount < maxRetries) return Flowable.timer(delayMillis, TimeUnit.MILLISECONDS);
            return Flowable.error(throwable);
        });
    }
}
