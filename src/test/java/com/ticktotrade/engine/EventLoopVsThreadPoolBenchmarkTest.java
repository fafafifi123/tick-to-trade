package com.ticktotrade.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares a lock-free single-producer/single-consumer ring buffer against a
 * lock-based {@link BlockingQueue} for handing one message at a time from a
 * producer thread to a consumer thread - the same handoff a tick-to-trade
 * pipeline does on every tick.
 * <p>
 * Both channels are given capacity 1, forcing strict ping-pong: the producer
 * cannot publish message N+1 until the consumer has taken message N. This
 * isolates the per-handoff synchronization cost (lock/condition-variable vs.
 * volatile-and-spin) instead of measuring queue backlog depth, which is what
 * a deep, unbounded queue would actually measure - a fast producer racing
 * ahead of a slow consumer makes any queue "look fast" simply because more
 * concurrent consumers drain the backlog quicker, regardless of the
 * underlying synchronization primitive.
 * <p>
 * A separate, non-asserted measurement using a thread pool sharing one
 * {@link LinkedBlockingQueue} is included to show that adding worker threads
 * increases aggregate throughput - a genuinely different axis from
 * single-hop latency, and not a counterexample to the ring buffer result.
 * <p>
 * Tagged {@code benchmark} and excluded from the default {@code mvn test}
 * run (see the surefire {@code excludedGroups} property in pom.xml) because
 * wall-clock comparisons are not deterministic build gates. Run explicitly with:
 * <pre>mvn test -Dsurefire.excludedGroups= -Dtest=EventLoopVsThreadPoolBenchmarkTest</pre>
 */
@Tag("benchmark")
class EventLoopVsThreadPoolBenchmarkTest {

    private static final int EVENTS = 200_000;
    private static final int WARMUP_EVENTS = 20_000;

    @Test
    void singleEventLoopRingBufferBeatsBlockingQueueForOneMessageAtATime() throws InterruptedException {
        // Warm up the JIT on both paths before taking real measurements.
        runPingPongBlockingQueue(WARMUP_EVENTS);
        runPingPongRingBuffer(WARMUP_EVENTS);

        double blockingQueueAvgNanos = runPingPongBlockingQueue(EVENTS);
        double ringBufferAvgNanos = runPingPongRingBuffer(EVENTS);
        double threadPoolAvgNanos = runThreadPoolBlockingQueue(EVENTS, 4, 4);

        System.out.printf("%n=== One-message-in-flight producer-to-consumer latency over %,d events ===%n", EVENTS);
        System.out.printf("1 producer/1 consumer + ArrayBlockingQueue (capacity 1)   : %10.1f ns%n", blockingQueueAvgNanos);
        System.out.printf("1 producer/1 consumer + lock-free ring buffer (capacity 1): %10.1f ns%n", ringBufferAvgNanos);
        System.out.printf("Ring buffer speedup vs blocking queue                     : %5.1fx%n",
                blockingQueueAvgNanos / ringBufferAvgNanos);
        System.out.printf("%n(FYI, not compared 1:1) 4x4 thread pool + LinkedBlockingQueue, deep queue: %10.1f ns avg%n"
                + "-- more threads drain backlog faster, which is a throughput effect, not a per-hop latency one.%n",
                threadPoolAvgNanos);

        assertTrue(ringBufferAvgNanos < blockingQueueAvgNanos,
                "a lock-free ring buffer should need less time per handoff than a lock-based blocking queue");
    }

    /** One producer thread, one consumer thread, at most one message in flight. */
    private double runPingPongBlockingQueue(int events) throws InterruptedException {
        BlockingQueue<Long> queue = new ArrayBlockingQueue<>(1);
        AtomicLong latencySumNanos = new AtomicLong();

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < events; i++) {
                    long sentAt = queue.take();
                    latencySumNanos.addAndGet(System.nanoTime() - sentAt);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();
        for (int i = 0; i < events; i++) {
            queue.put(System.nanoTime());
        }
        consumer.join();
        return latencySumNanos.get() / (double) events;
    }

    /** One producer thread, one consumer thread, at most one message in flight. */
    private double runPingPongRingBuffer(int events) throws InterruptedException {
        SpscRingBuffer ring = new SpscRingBuffer(1);
        AtomicLong latencySumNanos = new AtomicLong();

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < events; i++) {
                long sentAt = ring.poll();
                latencySumNanos.addAndGet(System.nanoTime() - sentAt);
            }
        });
        consumer.start();
        for (int i = 0; i < events; i++) {
            ring.publish(System.nanoTime());
        }
        consumer.join();
        return latencySumNanos.get() / (double) events;
    }

    /**
     * Multiple producer threads and multiple consumer threads sharing one deep,
     * lock-based queue. Printed for context only (see class javadoc) - not
     * asserted against the ping-pong numbers above, since it measures parallel
     * drain throughput rather than single-hop latency.
     */
    private double runThreadPoolBlockingQueue(int events, int producers, int consumers) throws InterruptedException {
        BlockingQueue<Long> queue = new LinkedBlockingQueue<>(10_000);
        AtomicLong latencySumNanos = new AtomicLong();
        long poisonPill = -1L;

        Thread[] consumerThreads = new Thread[consumers];
        for (int c = 0; c < consumers; c++) {
            consumerThreads[c] = new Thread(() -> {
                try {
                    while (true) {
                        long sentAt = queue.take();
                        if (sentAt == poisonPill) {
                            return;
                        }
                        latencySumNanos.addAndGet(System.nanoTime() - sentAt);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            consumerThreads[c].start();
        }

        int perProducer = events / producers;
        Thread[] producerThreads = new Thread[producers];
        for (int p = 0; p < producers; p++) {
            producerThreads[p] = new Thread(() -> {
                try {
                    for (int i = 0; i < perProducer; i++) {
                        queue.put(System.nanoTime());
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            producerThreads[p].start();
        }

        for (Thread t : producerThreads) {
            t.join();
        }
        for (int c = 0; c < consumers; c++) {
            queue.put(poisonPill);
        }
        for (Thread t : consumerThreads) {
            t.join();
        }
        return latencySumNanos.get() / (double) (perProducer * producers);
    }
}
