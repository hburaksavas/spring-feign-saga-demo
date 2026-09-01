package io.github.hburaksavas.sagademo.demo;

import io.github.hburaksavas.sagademo.demo.api.RemoteOperationRequest;
import io.github.hburaksavas.sagademo.demo.api.RemoteOperationResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RemoteOperationService {

    private final JdbcTemplate jdbc;

    public RemoteOperationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public RemoteOperationResponse execute(String sagaId, String stepId,
                                           String operationType,
                                           RemoteOperationRequest request) {
        try {
            jdbc.update("""
                    INSERT INTO DEMO_REMOTE_OPERATION
                      (STEP_ID, SAGA_ID, OPERATION_TYPE, CUSTOMER_NO,
                       AMOUNT, STATUS, CREATED_AT)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', SYSTIMESTAMP)
                    """, stepId, sagaId, operationType,
                    request.customerNo(), request.amount());
        } catch (DuplicateKeyException ignored) {
            // Original operation is idempotent by saga step id.
        }
        return current(stepId);
    }

    @Transactional
    public RemoteOperationResponse compensate(String sagaId, String stepId) {
        int updated = jdbc.update("""
                UPDATE DEMO_REMOTE_OPERATION
                   SET STATUS = 'COMPENSATED', COMPENSATED_AT = SYSTIMESTAMP
                 WHERE STEP_ID = ? AND SAGA_ID = ? AND STATUS = 'ACTIVE'
                """, stepId, sagaId);

        if (updated == 0) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM DEMO_REMOTE_OPERATION
                     WHERE STEP_ID = ? AND SAGA_ID = ? AND STATUS = 'COMPENSATED'
                    """, Integer.class, stepId, sagaId);
            if (count == null || count == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Original remote operation was not found");
            }
        }
        return current(stepId);
    }

    private RemoteOperationResponse current(String stepId) {
        return jdbc.queryForObject("""
                SELECT STEP_ID, STATUS FROM DEMO_REMOTE_OPERATION WHERE STEP_ID = ?
                """, (rs, rowNum) -> new RemoteOperationResponse(
                rs.getString("STEP_ID"), rs.getString("STATUS")), stepId);
    }
}
