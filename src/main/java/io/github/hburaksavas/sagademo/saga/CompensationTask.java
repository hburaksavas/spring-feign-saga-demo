package io.github.hburaksavas.sagademo.saga;

public record CompensationTask(
        String id,
        String sagaId,
        String stepId,
        String compensationUrl,
        String originalMethod,
        String originalUrl,
        String requestBody,
        String responseBody,
        int retryCount) {
}
