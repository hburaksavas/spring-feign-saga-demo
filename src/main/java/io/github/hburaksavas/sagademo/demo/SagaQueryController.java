package io.github.hburaksavas.sagademo.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sagas")
public class SagaQueryController {

    private final JdbcTemplate jdbc;

    public SagaQueryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT ID, SAGA_TYPE, STATUS, CORRELATION_ID,
                       STARTED_AT, COMPLETED_AT, LAST_ERROR
                  FROM SAGA_INSTANCE ORDER BY STARTED_AT DESC
                """);
    }

    @GetMapping("/{sagaId}")
    public Map<String, Object> detail(@PathVariable String sagaId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("saga", jdbc.queryForMap("""
                SELECT ID, SAGA_TYPE, STATUS, CORRELATION_ID,
                       STARTED_AT, COMPLETED_AT, LAST_ERROR
                  FROM SAGA_INSTANCE WHERE ID = ?
                """, sagaId));
        response.put("steps", jdbc.queryForList("""
                SELECT ID, STEP_ORDER, HTTP_METHOD, REQUEST_URL,
                       COMPENSATION_URL, RESPONSE_STATUS, STATUS,
                       CREATED_AT, UPDATED_AT
                  FROM SAGA_STEP WHERE SAGA_ID = ? ORDER BY STEP_ORDER
                """, sagaId));
        response.put("compensationTasks", jdbc.queryForList("""
                SELECT ID, STEP_ID, STATUS, RETRY_COUNT, NEXT_ATTEMPT_AT,
                       LOCKED_AT, LOCKED_BY, LAST_ERROR
                  FROM SAGA_COMP_TASK WHERE SAGA_ID = ? ORDER BY CREATED_AT
                """, sagaId));
        return response;
    }

    @GetMapping("/{sagaId}/remote-operations")
    public List<Map<String, Object>> remoteOperations(@PathVariable String sagaId) {
        return jdbc.queryForList("""
                SELECT STEP_ID, OPERATION_TYPE, CUSTOMER_NO, AMOUNT,
                       STATUS, CREATED_AT, COMPENSATED_AT
                  FROM DEMO_REMOTE_OPERATION
                 WHERE SAGA_ID = ? ORDER BY CREATED_AT
                """, sagaId);
    }
}
