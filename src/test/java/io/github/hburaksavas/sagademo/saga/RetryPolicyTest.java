package io.github.hburaksavas.sagademo.saga;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy();

    @Test
    void appliesExponentialBackoffWithJitterAndOneHourCap() {
        Duration first = policy.nextDelay(0);
        Duration fourth = policy.nextDelay(3);
        Duration capped = policy.nextDelay(20);

        assertThat(first).isBetween(Duration.ofSeconds(5), Duration.ofSeconds(6));
        assertThat(fourth).isBetween(Duration.ofSeconds(40), Duration.ofSeconds(49));
        assertThat(capped).isBetween(Duration.ofSeconds(3600), Duration.ofSeconds(4499));
    }
}
