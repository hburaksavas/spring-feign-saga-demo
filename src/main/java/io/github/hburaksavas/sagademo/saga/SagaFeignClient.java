package io.github.hburaksavas.sagademo.saga;

import feign.Client;
import feign.Request;
import feign.Response;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SagaFeignClient implements Client {

    private static final Set<String> COMPENSATABLE_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final int MAX_BODY_LENGTH = 64_000;

    private final Client delegate;
    private final SagaStore store;
    private final SagaProperties properties;

    public SagaFeignClient(Client delegate, SagaStore store, SagaProperties properties) {
        this.delegate = delegate;
        this.store = store;
        this.properties = properties;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        SagaContext.State context = SagaContext.current().orElse(null);
        if (context == null || !COMPENSATABLE_METHODS.contains(request.httpMethod().name())) {
            return delegate.execute(request, options);
        }

        String requestBody = bodyAsString(request.body());
        String compensationUrl = appendSuffixBeforeQuery(request.url(), properties.suffix());
        String stepId = store.createIntent(
                context.sagaId(), context.nextStepOrder(), request.httpMethod().name(),
                request.url(), compensationUrl, requestBody);

        Request enriched = withSagaHeaders(request, context.sagaId(), stepId);
        try {
            Response response = delegate.execute(enriched, options);
            byte[] responseBytes = response.body() == null
                    ? new byte[0]
                    : response.body().asInputStream().readAllBytes();
            String responseBody = abbreviate(new String(responseBytes, StandardCharsets.UTF_8));

            if (response.status() >= 200 && response.status() < 300) {
                store.markStepSuccess(stepId, response.status(), responseBody);
            } else if (response.status() >= 500) {
                store.markStepUnknown(stepId, response.status(), responseBody);
            } else {
                store.markStepFailed(stepId, response.status(), responseBody);
            }

            return response.toBuilder().body(responseBytes).build();
        } catch (IOException | RuntimeException exception) {
            store.markStepUnknown(stepId, exception);
            throw exception;
        }
    }

    private Request withSagaHeaders(Request request, String sagaId, String stepId) {
        Map<String, Collection<String>> headers = new LinkedHashMap<>(request.headers());
        headers.put("X-Saga-Id", List.of(sagaId));
        headers.put("X-Saga-Step-Id", List.of(stepId));
        headers.put("Idempotency-Key", List.of(stepId));

        return Request.create(
                request.httpMethod(), request.url(), headers, request.body(),
                request.charset(), request.requestTemplate());
    }

    private String appendSuffixBeforeQuery(String rawUrl, String suffix) {
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getPath().endsWith("/")
                    ? uri.getPath().substring(0, uri.getPath().length() - 1)
                    : uri.getPath();
            return new URI(uri.getScheme(), uri.getAuthority(), path + suffix,
                    uri.getQuery(), uri.getFragment()).toString();
        } catch (IllegalArgumentException | URISyntaxException exception) {
            throw new IllegalArgumentException("Cannot build compensation URL from: " + rawUrl, exception);
        }
    }

    private String bodyAsString(byte[] body) {
        return body == null ? null : abbreviate(new String(body, StandardCharsets.UTF_8));
    }

    private String abbreviate(String body) {
        if (body == null || body.length() <= MAX_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LENGTH);
    }
}
