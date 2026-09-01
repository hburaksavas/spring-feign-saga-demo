package io.github.hburaksavas.sagademo.saga;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RetryPolicy {

    public Duration nextDelay(int retryCount) {
        long exponentialSeconds = Math.min(
                3600,
                5L * (1L << Math.min(retryCount, 20)));
        long jitterBound = Math.max(1, exponentialSeconds / 4);
        long jitter = ThreadLocalRandom.current().nextLong(jitterBound);
        return Duration.ofSeconds(exponentialSeconds + jitter);
    }
}
