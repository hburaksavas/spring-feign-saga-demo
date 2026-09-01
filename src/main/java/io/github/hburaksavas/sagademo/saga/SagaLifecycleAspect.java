package io.github.hburaksavas.sagademo.saga;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Aspect
@Component
@Order(200)
public class SagaLifecycleAspect {

    private final SagaStore store;

    public SagaLifecycleAspect(SagaStore store) {
        this.store = store;
    }

    @Around("@annotation(sagaTransactional)")
    public Object manage(ProceedingJoinPoint joinPoint,
                         SagaTransactional sagaTransactional) throws Throwable {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("@SagaTransactional requires an active Spring transaction");
        }

        String correlationId = UUID.randomUUID().toString();
        String sagaId = store.createSaga(sagaTransactional.type(), correlationId);
        AtomicReference<String> failure = new AtomicReference<>("Local transaction rolled back");

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            store.completeSaga(sagaId);
                        } else {
                            store.scheduleCompensation(sagaId, failure.get());
                        }
                    }
                });

        SagaContext.open(sagaId);
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            failure.set(throwable.toString());
            throw throwable;
        } finally {
            SagaContext.close();
        }
    }
}
