package io.github.hburaksavas.sagademo.saga;

import com.fasterxml.jackson.databind.JsonNode;

public record CompensationCommand(
        String sagaId,
        String stepId,
        String originalMethod,
        String originalUrl,
        JsonNode originalRequest,
        JsonNode originalResponse,
        String reason) {
}
