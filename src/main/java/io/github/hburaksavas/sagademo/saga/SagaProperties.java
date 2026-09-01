package io.github.hburaksavas.sagademo.saga;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saga.compensation")
public record SagaProperties(
        String suffix,
        long pollDelayMs,
        int batchSize,
        int lockTimeoutSeconds,
        int maxRetries) {
}
