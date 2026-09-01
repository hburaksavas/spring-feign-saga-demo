package io.github.hburaksavas.sagademo.saga;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SagaStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;

    public SagaStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String createSaga(String type, String correlationId) {
        return requiresNew.execute(status -> {
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO SAGA_INSTANCE
                      (ID, SAGA_TYPE, STATUS, CORRELATION_ID, STARTED_AT)
                    VALUES (?, ?, 'ACTIVE', ?, SYSTIMESTAMP)
                    """, id, type, correlationId);
            return id;
        });
    }

    public String createIntent(String sagaId, int order, String method,
                               String url, String compensationUrl, String requestBody) {
        return requiresNew.execute(status -> {
            String stepId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO SAGA_STEP
                      (ID, SAGA_ID, STEP_ORDER, HTTP_METHOD, REQUEST_URL,
                       COMPENSATION_URL, REQUEST_BODY, STATUS, CREATED_AT, UPDATED_AT)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'INTENT', SYSTIMESTAMP, SYSTIMESTAMP)
                    """, stepId, sagaId, order, method, url, compensationUrl, requestBody);
            return stepId;
        });
    }

    public void markStepSuccess(String stepId, int httpStatus, String responseBody) {
        requiresNew.executeWithoutResult(status -> jdbc.update("""
                UPDATE SAGA_STEP
                   SET STATUS = 'SUCCESS', RESPONSE_STATUS = ?, RESPONSE_BODY = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE ID = ?
                """, httpStatus, responseBody, stepId));
    }

    public void markStepFailed(String stepId, Integer httpStatus, String responseBody) {
        requiresNew.executeWithoutResult(status -> jdbc.update("""
                UPDATE SAGA_STEP
                   SET STATUS = 'FAILED', RESPONSE_STATUS = ?, RESPONSE_BODY = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE ID = ?
                """, httpStatus, responseBody, stepId));
    }

    public void markStepUnknown(String stepId, Exception exception) {
        requiresNew.executeWithoutResult(status -> jdbc.update("""
                UPDATE SAGA_STEP
                   SET STATUS = 'UNKNOWN', RESPONSE_BODY = ?, UPDATED_AT = SYSTIMESTAMP
                 WHERE ID = ?
                """, abbreviate(exception.toString()), stepId));
    }

    public void markStepUnknown(String stepId, int httpStatus, String responseBody) {
        requiresNew.executeWithoutResult(status -> jdbc.update("""
                UPDATE SAGA_STEP
                   SET STATUS = 'UNKNOWN', RESPONSE_STATUS = ?, RESPONSE_BODY = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE ID = ?
                """, httpStatus, responseBody, stepId));
    }

    public void completeSaga(String sagaId) {
        requiresNew.executeWithoutResult(status -> jdbc.update("""
                UPDATE SAGA_INSTANCE
                   SET STATUS = 'COMPLETED', COMPLETED_AT = SYSTIMESTAMP
                 WHERE ID = ?
                """, sagaId));
    }

    public void scheduleCompensation(String sagaId, String error) {
        requiresNew.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE SAGA_INSTANCE
                       SET STATUS = 'COMPENSATING', LAST_ERROR = ?
                     WHERE ID = ?
                    """, abbreviate(error), sagaId);

            List<String> stepIds = jdbc.queryForList("""
                    SELECT ID
                      FROM SAGA_STEP
                     WHERE SAGA_ID = ? AND STATUS = 'SUCCESS'
                     ORDER BY STEP_ORDER DESC
                    """, String.class, sagaId);

            for (String stepId : stepIds) {
                try {
                    jdbc.update("""
                            INSERT INTO SAGA_COMP_TASK
                              (ID, SAGA_ID, STEP_ID, STATUS, NEXT_ATTEMPT_AT,
                               RETRY_COUNT, CREATED_AT, UPDATED_AT)
                            VALUES (?, ?, ?, 'PENDING', SYSTIMESTAMP, 0,
                                    SYSTIMESTAMP, SYSTIMESTAMP)
                            """, UUID.randomUUID().toString(), sagaId, stepId);
                    jdbc.update("""
                            UPDATE SAGA_STEP SET STATUS = 'COMPENSATION_PENDING',
                                UPDATED_AT = SYSTIMESTAMP WHERE ID = ?
                            """, stepId);
                } catch (DuplicateKeyException ignored) {
                    // Transaction synchronization may be invoked defensively more than once.
                }
            }
        });
    }

    public List<CompensationTask> claimBatch(String workerId, int batchSize,
                                              Duration lockTimeout) {
        return requiresNew.execute(status -> {
            Instant staleBefore = Instant.now().minus(lockTimeout);
            List<CompensationTask> tasks = jdbc.query("""
                    SELECT T.ID, T.SAGA_ID, T.STEP_ID, S.COMPENSATION_URL,
                           S.HTTP_METHOD, S.REQUEST_URL, S.REQUEST_BODY,
                           S.RESPONSE_BODY, T.RETRY_COUNT
                      FROM SAGA_COMP_TASK T
                      JOIN SAGA_STEP S ON S.ID = T.STEP_ID
                     WHERE T.ID IN (
                           SELECT C.ID
                             FROM SAGA_COMP_TASK C
                             JOIN SAGA_STEP CS ON CS.ID = C.STEP_ID
                            WHERE ((C.STATUS IN ('PENDING', 'RETRY_WAIT')
                                    AND C.NEXT_ATTEMPT_AT <= SYSTIMESTAMP)
                               OR (C.STATUS = 'PROCESSING' AND C.LOCKED_AT < ?))
                              AND NOT EXISTS (
                                  SELECT 1
                                    FROM SAGA_COMP_TASK PREV
                                    JOIN SAGA_STEP PREV_STEP ON PREV_STEP.ID = PREV.STEP_ID
                                   WHERE PREV.SAGA_ID = C.SAGA_ID
                                     AND PREV.STATUS <> 'COMPLETED'
                                     AND PREV_STEP.STEP_ORDER > CS.STEP_ORDER
                              )
                            ORDER BY C.NEXT_ATTEMPT_AT
                     )
                       AND ROWNUM <= ?
                     FOR UPDATE OF T.STATUS SKIP LOCKED
                    """, (rs, rowNum) -> new CompensationTask(
                            rs.getString("ID"),
                            rs.getString("SAGA_ID"),
                            rs.getString("STEP_ID"),
                            rs.getString("COMPENSATION_URL"),
                            rs.getString("HTTP_METHOD"),
                            rs.getString("REQUEST_URL"),
                            rs.getString("REQUEST_BODY"),
                            rs.getString("RESPONSE_BODY"),
                            rs.getInt("RETRY_COUNT")),
                    Timestamp.from(staleBefore), batchSize);

            for (CompensationTask task : tasks) {
                jdbc.update("""
                        UPDATE SAGA_COMP_TASK
                           SET STATUS = 'PROCESSING', LOCKED_BY = ?,
                               LOCKED_AT = SYSTIMESTAMP, UPDATED_AT = SYSTIMESTAMP
                         WHERE ID = ?
                        """, workerId, task.id());
            }
            return tasks;
        });
    }

    public void markCompensated(CompensationTask task) {
        requiresNew.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE SAGA_COMP_TASK SET STATUS = 'COMPLETED',
                        LOCKED_BY = NULL, LOCKED_AT = NULL, UPDATED_AT = SYSTIMESTAMP
                     WHERE ID = ?
                    """, task.id());
            jdbc.update("""
                    UPDATE SAGA_STEP SET STATUS = 'COMPENSATED',
                        UPDATED_AT = SYSTIMESTAMP WHERE ID = ?
                    """, task.stepId());

            Integer remaining = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM SAGA_COMP_TASK
                     WHERE SAGA_ID = ? AND STATUS <> 'COMPLETED'
                    """, Integer.class, task.sagaId());
            if (remaining != null && remaining == 0) {
                jdbc.update("""
                        UPDATE SAGA_INSTANCE SET STATUS = 'COMPENSATED',
                            COMPLETED_AT = SYSTIMESTAMP WHERE ID = ?
                        """, task.sagaId());
            }
        });
    }

    public void scheduleRetry(CompensationTask task, Duration delay, Exception error) {
        requiresNew.executeWithoutResult(status -> jdbc.update("""
                UPDATE SAGA_COMP_TASK
                   SET STATUS = 'RETRY_WAIT', RETRY_COUNT = RETRY_COUNT + 1,
                       NEXT_ATTEMPT_AT = ?, LOCKED_BY = NULL, LOCKED_AT = NULL,
                       LAST_ERROR = ?, UPDATED_AT = SYSTIMESTAMP
                 WHERE ID = ?
                """, Timestamp.from(Instant.now().plus(delay)),
                abbreviate(error.toString()), task.id()));
    }

    public void markManualIntervention(CompensationTask task, Exception error) {
        requiresNew.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE SAGA_COMP_TASK
                       SET STATUS = 'MANUAL_INTERVENTION', LOCKED_BY = NULL,
                           LOCKED_AT = NULL, LAST_ERROR = ?, UPDATED_AT = SYSTIMESTAMP
                     WHERE ID = ?
                    """, abbreviate(error.toString()), task.id());
            jdbc.update("""
                    UPDATE SAGA_INSTANCE SET STATUS = 'MANUAL_INTERVENTION',
                        LAST_ERROR = ? WHERE ID = ?
                    """, abbreviate(error.toString()), task.sagaId());
        });
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
