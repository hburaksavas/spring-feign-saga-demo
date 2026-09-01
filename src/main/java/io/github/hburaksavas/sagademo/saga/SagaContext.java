package io.github.hburaksavas.sagademo.saga;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class SagaContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private SagaContext() {
    }

    public static void open(String sagaId) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested sagas are not supported");
        }
        CURRENT.set(new State(sagaId, new AtomicInteger()));
    }

    public static Optional<State> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void close() {
        CURRENT.remove();
    }

    public record State(String sagaId, AtomicInteger sequence) {
        public int nextStepOrder() {
            return sequence.incrementAndGet();
        }
    }
}
