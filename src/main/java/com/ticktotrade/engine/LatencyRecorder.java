package com.ticktotrade.engine;

import java.util.Arrays;

/**
 * Fixed-capacity, allocation-free latency recorder for the hot path. Once the
 * ring buffer fills, older samples are overwritten - this is a demo/testing
 * aid, not a production metrics pipeline (see README for HDRHistogram as the
 * real-world replacement).
 */
public final class LatencyRecorder {

    private final long[] samplesNanos;
    private int nextIndex;
    private int count;

    public LatencyRecorder(int capacity) {
        this.samplesNanos = new long[capacity];
    }

    public void record(long latencyNanos) {
        samplesNanos[nextIndex] = latencyNanos;
        nextIndex = (nextIndex + 1) % samplesNanos.length;
        if (count < samplesNanos.length) {
            count++;
        }
    }

    public int count() {
        return count;
    }

    public long maxNanos() {
        long max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, samplesNanos[i]);
        }
        return max;
    }

    public double meanNanos() {
        if (count == 0) {
            return 0;
        }
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += samplesNanos[i];
        }
        return (double) sum / count;
    }

    public long percentileNanos(double percentile) {
        if (count == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(samplesNanos, count);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index];
    }
}
