package io.github.ag0708.stridequeue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Hot long fields separated by 128 bytes inside one backing array. */
final class StripedLongs {
    private static final VarHandle ELEMENT = MethodHandles.arrayElementVarHandle(long[].class);
    private static final int STRIDE = 16;

    private final long[] values;

    StripedLongs(long... initialValues) {
        values = new long[Math.multiplyExact(initialValues.length, STRIDE)];
        for (int index = 0; index < initialValues.length; index++) {
            values[offset(index)] = initialValues[index];
        }
    }

    long getVolatile(int index) {
        return (long) ELEMENT.getVolatile(values, offset(index));
    }

    boolean compareAndSet(int index, long expected, long update) {
        return ELEMENT.compareAndSet(values, offset(index), expected, update);
    }

    void setRelease(int index, long update) {
        ELEMENT.setRelease(values, offset(index), update);
    }

    private int offset(int index) {
        return Math.multiplyExact(index, STRIDE);
    }
}
