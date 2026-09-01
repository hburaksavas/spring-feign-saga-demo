package io.github.hburaksavas.sagademo.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
public class CompensationWorker {

    private static final Logger log = LoggerFactory.getLogger(CompensationWorker.class);

    private final SagaStore store;
    private final RetryPolicy retryPolicy;
    private final SagaProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String workerId;

    public CompensationWorker(
            SagaStore store,
            RetryPolicy retryPolicy,
            SagaProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${spring.application.name}") String applicationName,
            @Value("${HOSTNAME:local}") String hostname) {
        this.store = store;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.workerId = applicationName + "-" + hostname;
    }

    @Scheduled(fixedDelayString = "${saga.compensation.poll-delay-ms:5000}")
    public void poll() {
        List<CompensationTask> tasks = store.claimBatch(
                workerId,
                properties.batchSize(),
                Duration.ofSeconds(properties.lockTimeoutSeconds()));

        tasks.forEach(this::process);
    }

    private void process(CompensationTask task) {
        try {
            CompensationCommand command = new CompensationCommand(
                    task.sagaId(), task.stepId(), task.originalMethod(), task.originalUrl(),
                    parseJson(task.requestBody()), parseJson(task.responseBody()),
                    "Local transaction rolled back");

            restClient.post()
                    .uri(task.compensationUrl())
                    .header("X-Saga-Id", task.sagaId())
                    .header("X-Saga-Step-Id", task.stepId())
                    .header("Idempotency-Key", "compensation-" + task.stepId())
                    .header("X-Compensation-Attempt", Integer.toString(task.retryCount() + 1))
                    .body(command)
                    .retrieve()
                    .toBodilessEntity();

            store.markCompensated(task);
            log.info("Compensated saga={} step={}", task.sagaId(), task.stepId());
        } catch (Exception exception) {
            handleFailure(task, exception);
        }
    }

    private void handleFailure(CompensationTask task, Exception exception) {
        int nextAttempt = task.retryCount() + 1;
        if (nextAttempt >= properties.maxRetries() || !isRetryable(exception)) {
            store.markManualIntervention(task, exception);
            log.error("Compensation requires manual intervention. saga={} step={}",
                    task.sagaId(), task.stepId(), exception);
            return;
        }

        Duration delay = retryPolicy.nextDelay(task.retryCount());
        store.scheduleRetry(task, delay, exception);
        log.warn("Compensation retry scheduled. saga={} step={} delay={}",
                task.sagaId(), task.stepId(), delay);
    }

    private boolean isRetryable(Exception exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof HttpStatusCodeException statusException) {
            HttpStatusCode status = statusException.getStatusCode();
            return status.is5xxServerError()
                    || status.value() == 408
                    || status.value() == 429;
        }
        return false;
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }
}
