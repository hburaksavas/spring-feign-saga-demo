package io.github.hburaksavas.sagademo.saga;

import feign.Client;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaFeignClientTest {

    @AfterEach
    void cleanContext() {
        SagaContext.close();
    }

    @Test
    void recordsSuccessfulPostAndRebuildsConsumedResponseBody() throws Exception {
        Client delegate = mock(Client.class);
        SagaStore store = mock(SagaStore.class);
        SagaProperties properties = new SagaProperties("/compensation", 5000, 20, 120, 8);
        SagaFeignClient client = new SagaFeignClient(delegate, store, properties);

        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://localhost/items?source=test",
                Map.of("Content-Type", java.util.List.of("application/json")),
                "{\"amount\":10}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8,
                null);
        Response delegateResponse = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .body("{\"status\":\"ACTIVE\"}", StandardCharsets.UTF_8)
                .build();

        when(store.createIntent(any(), anyInt(), any(), any(), any(), any()))
                .thenReturn("step-1");
        when(delegate.execute(any(), any())).thenReturn(delegateResponse);
        SagaContext.open("saga-1");

        Response result = client.execute(request, new Request.Options());

        assertThat(result.body().asInputStream().readAllBytes())
                .asString(StandardCharsets.UTF_8)
                .isEqualTo("{\"status\":\"ACTIVE\"}");
        verify(store).createIntent(
                "saga-1", 1, "POST", "http://localhost/items?source=test",
                "http://localhost/items/compensation?source=test", "{\"amount\":10}");
        verify(store).markStepSuccess("step-1", 200, "{\"status\":\"ACTIVE\"}");
    }
}
