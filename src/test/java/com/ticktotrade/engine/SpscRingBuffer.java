package com.ticktotrade.engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal single-producer/single-consumer ring buffer used only by the
 * benchmark test to contrast with a lock-based {@code BlockingQueue}. Safety
 * relies on there being exactly one publisher thread and one consumer
 * thread: the write cursor is published with {@code lazySet} (a release
 * fence) after the slot is written, and the read cursor is published the
 * same way after the slot is consumed, giving each side a happens-before
 * edge without ever taking a lock.
 */
final class SpscRingBuffer {

    private final long[] slots;
    private final int mask;
    private final AtomicLong writeSeq = new AtomicLong();
    private final AtomicLong readSeq = new AtomicLong();

    SpscRingBuffer(int capacityPowerOfTwo) {
        this.slots = new long[capacityPowerOfTwo];
        this.mask = capacityPowerOfTwo - 1;
    }

    void publish(long value) {
        long seq = writeSeq.get();
        while (seq - readSeq.get() >= slots.length) {
            Thread.onSpinWait();
        }
        slots[(int) (seq & mask)] = value;
        writeSeq.lazySet(seq + 1);
    }

    long poll() {
        long seq = readSeq.get();
        while (seq >= writeSeq.get()) {
            Thread.onSpinWait();
        }
        long value = slots[(int) (seq & mask)];
        readSeq.lazySet(seq + 1);
        return value;
    }
}
