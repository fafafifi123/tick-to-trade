package com.ticktotrade.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyRecorderTest {

    @Test
    void reportsZeroesWhenEmpty() {
        LatencyRecorder recorder = new LatencyRecorder(4);

        assertEquals(0, recorder.count());
        assertEquals(0L, recorder.maxNanos());
        assertEquals(0.0, recorder.meanNanos());
        assertEquals(0L, recorder.percentileNanos(99));
    }

    @Test
    void tracksCountMeanMaxAndPercentileWithinCapacity() {
        LatencyRecorder recorder = new LatencyRecorder(4);

        recorder.record(100);
        recorder.record(200);
        recorder.record(300);

        assertEquals(3, recorder.count());
        assertEquals(300L, recorder.maxNanos());
        assertEquals(200.0, recorder.meanNanos());
        assertEquals(300L, recorder.percentileNanos(100));
        assertEquals(100L, recorder.percentileNanos(1));
    }

    @Test
    void overwritesOldestSampleOnceCapacityIsExceeded() {
        LatencyRecorder recorder = new LatencyRecorder(3);

        recorder.record(10);
        recorder.record(20);
        recorder.record(30);
        recorder.record(40);

        assertEquals(3, recorder.count());
        assertEquals(40L, recorder.maxNanos());
        assertEquals(30.0, recorder.meanNanos());
    }
}
