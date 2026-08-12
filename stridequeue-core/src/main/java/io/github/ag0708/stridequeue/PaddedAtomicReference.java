package io.github.ag0708.stridequeue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** A reference cursor kept away from other hot fields on common JVM layouts. */
final class PaddedAtomicReference<T> {
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(PaddedAtomicReference.class, "value", Object.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @SuppressWarnings("unused")
    private long p00, p01, p02, p03, p04, p05, p06, p07;

    private volatile Object value;

    @SuppressWarnings("unused")
    private long p10, p11, p12, p13, p14, p15, p16, p17;

    PaddedAtomicReference(T initialValue) {
        value = initialValue;
    }

    @SuppressWarnings("unchecked")
    T getVolatile() {
        return (T) VALUE.getVolatile(this);
    }

    boolean compareAndSet(T expected, T update) {
        return VALUE.compareAndSet(this, expected, update);
    }
}
