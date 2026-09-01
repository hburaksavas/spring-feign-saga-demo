package io.github.hburaksavas.sagademo.demo;

import io.github.hburaksavas.sagademo.demo.api.RemoteOperationRequest;
import io.github.hburaksavas.sagademo.demo.api.RemoteOperationResponse;
import io.github.hburaksavas.sagademo.saga.CompensationCommand;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class RemoteOperationController {

    private final RemoteOperationService service;

    public RemoteOperationController(RemoteOperationService service) {
        this.service = service;
    }

    @PostMapping("/limits/reservations")
    public RemoteOperationResponse reserveLimit(
            @RequestHeader("X-Saga-Id") String sagaId,
            @RequestHeader("X-Saga-Step-Id") String stepId,
            @RequestBody RemoteOperationRequest request) {
        return service.execute(sagaId, stepId, "LIMIT_RESERVATION", request);
    }

    @PostMapping("/limits/reservations/compensation")
    public RemoteOperationResponse releaseLimit(
            @RequestHeader("X-Saga-Id") String sagaId,
            @RequestHeader("X-Saga-Step-Id") String stepId,
            @RequestBody CompensationCommand ignored) {
        return service.compensate(sagaId, stepId);
    }

    @PostMapping("/accounting/entries")
    public RemoteOperationResponse createAccountingEntry(
            @RequestHeader("X-Saga-Id") String sagaId,
            @RequestHeader("X-Saga-Step-Id") String stepId,
            @RequestBody RemoteOperationRequest request) {
        return service.execute(sagaId, stepId, "ACCOUNTING_ENTRY", request);
    }

    @PostMapping("/accounting/entries/compensation")
    public RemoteOperationResponse reverseAccountingEntry(
            @RequestHeader("X-Saga-Id") String sagaId,
            @RequestHeader("X-Saga-Step-Id") String stepId,
            @RequestBody CompensationCommand ignored) {
        return service.compensate(sagaId, stepId);
    }
}
