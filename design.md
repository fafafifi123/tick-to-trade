# Single event loop + ring buffer vs multi-threaded + queue

Reference notes for `EventLoopVsThreadPoolBenchmarkTest.java` / `SpscRingBuffer.java` /
`result.md`. Written as tables and bullets on purpose - the prose version of this
discussion was confusing to read back.

## 1. The two patterns, side by side

| | Single event loop + ring buffer | Multi-threaded + blocking queue |
|---|---|---|
| Sync primitive | `volatile`/`AtomicLong` cursor, busy-spin read | `ReentrantLock` + `Condition` |
| Wait mechanism | Spin (`Thread.onSpinWait()`), never sleeps | `park()`/`unpark()` - real OS syscall, thread actually sleeps |
| CPU while idle | 100% on the consumer's core, always | ~0%, OS wakes it only when needed |
| Measured handoff cost (this repo, this machine) | ~150-300 ns | ~11,000-14,000 ns |
| Threads supported | 1 producer, 1 consumer only (SPSC) | Any number of producers/consumers |
| Failure mode when misused | Silent latency spikes, wasted CPU/power, heat | Slightly worse throughput - fails loud and boring |
| Requires | A dedicated physical core per spinning thread | Nothing special |

## 2. When to reach for single event loop + ring buffer

Use it only when **all** of these are true:

- [ ] You have a real async boundary to optimize (e.g. network I/O thread → strategy thread), not just a function call you could make direct.
- [ ] You've already confirmed the work on each side (strategy eval, order send) is fast - the handoff itself is now the bottleneck.
- [ ] It's genuinely one producer and one consumer. (Need many-to-many? Use a queue - don't hand-roll a worse Disruptor.)
- [ ] You can guarantee a **dedicated physical core** for every spinning thread - not a shared vCPU, not a throttled container, not hyperthread siblings of each other.
- [ ] Someone on the team can actually debug lock-free code (memory ordering, false sharing) when it breaks at 2am.
- [ ] Latency predictability matters more than raw throughput for this specific path.

If any box is unchecked, use a `BlockingQueue` instead.

## 3. When single event loop / ring buffer is SLOWER (or just a bad trade) vs multi-threading/queue

| Condition | Why it hurts the ring buffer specifically | What happens instead |
|---|---|---|
| **No spare physical core** (see §4) | Spinning thread gets preempted on a timer it doesn't control | Involuntary context switches - same cost as blocking, but non-deterministic |
| **Running in a container with a CPU limit/quota** | cgroup throttles the whole process once quota is spent | Process freezes mid-spin; producer freezes too |
| **Cloud VM ("vCPU")** | vCPU is often a hyperthread shared with another tenant | Noisy-neighbor jitter you can't see or control |
| **Workload is throughput-bound, not latency-bound** | One consumer thread processes strictly sequentially | A thread pool draining a shared queue in parallel wins on messages/sec |
| **Multiple producers or multiple consumers needed** | SPSC ring buffer doesn't support this | `BlockingQueue`/`ConcurrentLinkedQueue` already solve MPMC correctly |
| **Downstream work (I/O, strategy calc) dominates cost** | Handoff is 150ns either way; the real cost is milliseconds elsewhere | Optimizing the queue is optimizing something that's already noise |
| **Team unfamiliar with lock-free code** | Subtle bugs (torn reads, missed happens-before) are hard to catch in review/test | A boring, correct `BlockingQueue` beats a fast-but-wrong ring buffer |
| **Power/heat/cost matters** (laptops, battery, cloud billing) | Busy-spin burns 100% CPU continuously, idle or not | Blocking queue uses ~0% CPU when there's nothing to do |

## 4. Deep dive: "no spare core" (the most common way this goes wrong)

A busy-spin consumer only wins if its core is *exclusively* its own. Here's what breaks that assumption in practice:

| Scenario | What actually happens |
|---|---|
| **More runnable threads than cores** | OS time-slices. Consumer gets a quantum (~15ms Windows, few ms Linux CFS), then is force-preempted - even if it was mid-spin waiting on the very next message. |
| **Hyperthreading, both threads on one physical core** | Producer and consumer share one execution engine, ALUs, L1/L2 cache. Both spinning at 100% congests the shared front-end - they slow each other down more than if one had simply parked and freed the core. |
| **Kubernetes pod with a CPU limit** | cgroup enforces a CPU-time quota per period. A spinning thread burns through it instantly, then the *entire container* (producer included) freezes until the next period. |
| **Cloud "vCPU"** | Often just a hyperthread on a physical core shared with another tenant's VM. Zero control over what else competes for it - the classic noisy-neighbor problem. |
| **Real low-latency systems (Disruptor, Aeron, Chronicle) avoid this by** | Pinning each hot thread to one specific core (`taskset`/JNA affinity), `isolcpus`/`nohz_full` kernel params so the scheduler never puts anything else there, and tuning IRQ affinity away from those cores. |

Rule of thumb: **count of busy-spinning threads < physical cores reserved for them.** In a container/cloud VM, verify the CPU limit actually maps to that many *dedicated* cores - not a time-sliced fraction of shared ones.

## 5. Real numbers (see `result.md` for full detail)

One producer, one consumer, capacity-1 channel (strict ping-pong, so the number reflects per-handoff cost, not queue backlog):

| Run | ArrayBlockingQueue | Ring buffer | Speedup |
|---|---:|---:|---:|
| 1 | 11,079.2 ns | 154.4 ns | 71.8x |
| 2 | 13,761.6 ns | 214.5 ns | 64.1x |
| 3 | 11,957.2 ns | 296.3 ns | 40.4x |

The ring buffer's own run-to-run variance (154 → 296 ns) is itself a small example of §4: even on a 6-core/12-thread machine with nothing else major running, the OS scheduler didn't guarantee identical core placement every run.

4×4 thread pool sharing one deep `LinkedBlockingQueue` (throughput scenario, not comparable 1:1 - see `result.md`): ~1.16-1.18 ms average, because it's measuring backlog drain time with a fast producer outrunning consumers, not handoff cost.

## 6. One-line decision rule

> Optimizing a queue you haven't proven is the bottleneck, on hardware you don't control the scheduling of, for a topology it doesn't support (many producers/consumers) - is the classic way this becomes overkill. Default to `BlockingQueue`. Reach for a ring buffer only after profiling shows the handoff itself is the cost, and only with a core you can dedicate to it.
