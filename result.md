# Benchmark result: lock-free ring buffer vs blocking queue

Real numbers from three consecutive, unedited runs of
`EventLoopVsThreadPoolBenchmarkTest` on this machine. Command used:

```
mvn test -Dsurefire.excludedGroups= -Dtest=EventLoopVsThreadPoolBenchmarkTest
```

## Environment

- OS: Microsoft Windows 11 Home 10.0.26200
- CPU: AMD Ryzen 5 7500F, 6 physical cores / 12 logical
- JDK: 21.0.11+9-LTS (Oracle)
- Maven: 3.9.16
- Date: 2026-07-24

## Method

One producer thread, one consumer thread, channel capacity fixed at **1**
(strict ping-pong - the producer cannot publish message N+1 until the
consumer has taken message N). This isolates the per-handoff synchronization
cost instead of queue backlog depth. 200,000 timed events per run, preceded
by a 20,000-event JIT warmup that is not measured. Latency = consumer's
`System.nanoTime()` at receipt minus producer's `System.nanoTime()` at send.

Two channels compared:
- `ArrayBlockingQueue` (capacity 1) - lock + condition variable, backed by a real OS park/unpark when a thread has to wait.
- `SpscRingBuffer` (capacity 1) - single-producer/single-consumer array with `AtomicLong` cursors, busy-spin wait, no locks, no syscalls.

## Results

| Run | ArrayBlockingQueue avg latency | Ring buffer avg latency | Speedup |
|---|---:|---:|---:|
| 1 | 11,079.2 ns | 154.4 ns | 71.8x |
| 2 | 13,761.6 ns | 214.5 ns | 64.1x |
| 3 | 11,957.2 ns | 296.3 ns | 40.4x |

All three runs: `Tests run: 1, Failures: 0` — the assertion
`ringBufferAvgNanos < blockingQueueAvgNanos` held every time.

Blocking-queue latency stayed in the same ballpark across runs
(~11-14 microseconds), consistent with it being dominated by a fixed OS
context-switch cost (park/unpark). Ring buffer latency varied more in
absolute terms (154-296 ns) because at that scale it's mostly measuring
cache-line and scheduler jitter rather than a fixed syscall cost - but it
never came close to blocking-queue territory, and stayed 40x-72x faster
across every run.

## Secondary measurement (not part of the pass/fail assertion)

A 4-producer/4-consumer thread pool sharing one deep (`10,000` capacity)
`LinkedBlockingQueue` was also measured for context:

| Run | Avg latency |
|---|---:|
| 1 | 1,161,853.6 ns |
| 3 | 1,180,896.8 ns |

This number is an order of magnitude worse than even the single-hop
blocking queue - but that's not a contradiction. With a deep queue, average
latency is dominated by how long messages sit in the backlog waiting to be
drained, and a fast producer racing ahead of the consumer(s) inflates that
number regardless of the synchronization primitive. It measures aggregate
throughput/backlog dynamics, not per-handoff cost, which is why it isn't
compared 1:1 against the ping-pong numbers above.

## Conclusion

On this machine, a lock-free single-producer/single-consumer ring buffer
handed off one message in **~150-300 ns**, while an `ArrayBlockingQueue`
doing the same job took **~11,000-14,000 ns** — a real, repeatable
**40x-72x** difference, driven by avoiding the OS-level park/unpark that a
lock-based queue pays for every wait. The trade-off: the ring buffer's
consumer thread busy-spins, so this only pays off when a spare CPU core can
be dedicated to it.
