# tick-to-trade

A minimal, fully-testable Java tick-to-trade pipeline: **market data → alpha → broker order**,
with every boundary behind an interface so it can be mocked, and the tick-to-order
path instrumented to prove it stays well under a 100ms latency budget.

## What's here

```
src/main/java/com/ticktotrade/
  model/     Tick, Order, Side, Signal, OrderResult          (immutable records)
  feed/      MarketDataFeed, TickListener                    (port)
             SimulatedMarketDataFeed                         (in-memory adapter)
  alpha/     AlphaStrategy                                   (port)
             MovingAverageCrossoverStrategy                  (example strategy)
  broker/    BrokerGateway                                   (port)
             SimulatedBrokerGateway                          (in-memory adapter)
  engine/    TradingEngine                                   (wires feed -> alpha -> broker)
             LatencyRecorder                                 (nanosecond latency samples)
  Main.java  end-to-end demo: 5,000 simulated ticks through the engine

src/test/java/com/ticktotrade/
  engine/TradingEngineTest.java              engine wiring, with alpha + broker MOCKED (Mockito)
  alpha/MovingAverageCrossoverStrategyTest.java  strategy logic in isolation
  feed/SimulatedMarketDataFeedTest.java          feed dispatch logic in isolation
```

### Run it

```
mvn test                                  # run all unit tests
mvn compile && java -cp target/classes com.ticktotrade.Main   # run the demo
```

Sample demo output on this machine (Java 21, no JIT warm-up, first run):

```
Ticks processed: 5000
Orders sent: 346
Tick-to-order latency: mean=1157ns p99=2800ns max=252500ns (n=346)
```

Max observed latency is ~0.25ms — three orders of magnitude under the 100ms target,
even running cold (no warm-up loop) on an ordinary dev machine with a synchronous,
single-threaded, no-framework pipeline. Getting under 100ms was never the hard part;
the design considerations below explain what *would* get hard if the target were 1ms
or 100µs instead.

## Architecture: ports & adapters (hexagonal)

`TradingEngine` depends only on the `AlphaStrategy` and `BrokerGateway` **interfaces**,
never on a concrete feed, strategy, or broker. This is the one architectural decision
everything else follows from:

- **Testability**: `TradingEngineTest` mocks `AlphaStrategy` and `BrokerGateway` with
  Mockito and never touches a real market data feed or network socket. Every test
  runs in milliseconds, deterministically, with no external dependency.
- **Swappability**: `SimulatedMarketDataFeed` and `SimulatedBrokerGateway` are
  in-memory adapters used by tests and the demo. A real deployment swaps in a FIX
  market-data adapter and a FIX/REST broker adapter — `TradingEngine` doesn't change.
- **No framework**: no Spring, no DI container. Everything is wired by hand with
  constructors in `Main.java`. At this scale a DI framework buys nothing but classloading
  overhead and indirection; wire it by hand until the object graph actually gets painful.

## Design considerations

**Why records for the domain model.** `Tick`, `Order`, `Signal` etc. are immutable
Java records: no setters, no partially-constructed state, safe to hand across threads
without defensive copying. Immutability also makes the hot path easier to reason about
under concurrency — nothing mutates an event after creation.

**Why the alpha strategy owns its own state instead of the engine.** `TradingEngine`
is stateless with respect to trading logic; `MovingAverageCrossoverStrategy` owns its
circular buffers internally. This keeps the engine a pure "wire things together and
measure" component, and lets you unit-test a strategy's math completely separately
from the engine's wiring (see `MovingAverageCrossoverStrategyTest`).

**Why fixed-size arrays instead of `ArrayDeque`/`LinkedList` in the strategy.**
The moving-average windows are pre-allocated `double[]` circular buffers. Once warmed
up, `evaluate()` allocates nothing. On a hot path called once per tick, every
allocation is a future GC pause; a strategy that boxes into `Double` objects or grows
a `List` per tick will eventually pay for it in a stop-the-world (or even
concurrent-collector) pause at the worst possible moment. This is more headroom than a
100ms budget needs today — it's here because it's cheap to do correctly from the start
and expensive to retrofit later.

**Why `Signal.none()` instead of returning `null`.** A "no trade" outcome is a normal,
frequent case (most ticks don't trigger a trade), not an edge case — modeling it as a
real value means the engine's hot path has no null-check-or-NPE branch and no
`Optional` allocation.

**Why latency is measured from `tick.arrivalTimestampNanos()`, not from when the
strategy started evaluating.** `Tick` carries both `exchangeTimestampNanos` (when the
event happened at the source) and `arrivalTimestampNanos` (when this process observed
it). The engine measures *processing* latency (arrival → order-sent); the gap between
`exchangeTimestampNanos` and `arrivalTimestampNanos` is *feed/network* latency and
belongs to a different budget owned by the feed adapter, not the engine. Conflating
the two hides where time is actually going.

**Why `LatencyRecorder` is a hand-rolled ring buffer, not a real metrics library.**
It's intentionally the simplest thing that lets a test assert "p99 latency is bounded."
A real system should use **HDRHistogram** (see below) for accurate percentile tracking
across millions of samples without the memory cost of storing every raw sample.

**Why single-threaded / synchronous dispatch.** `SimulatedMarketDataFeed.publish()`
calls listeners synchronously on the caller's thread, and `TradingEngine.onTick()` runs
alpha evaluation and the broker call inline. This is the simplest correct design and
is more than fast enough for a 100ms budget. It is *not* how you'd design for
microsecond latency — see "if you need to go faster" below.

**Why `BrokerGateway.send()` is synchronous/blocking in the interface.** Simpler to
reason about and to mock. The real cost of a broker call is dominated by network RTT,
which no amount of clever Java changes — that's an infrastructure/deployment decision
(co-location, direct market access), not a code-shape decision. If you need to keep
accepting ticks while an order is in flight, that's the point where you'd introduce
async (a `CompletableFuture<OrderResult>` return type, or a queue), and it's called out
below as a deliberate non-goal for this simple version.

**What's deliberately not built.** No risk checks (max position, fat-finger limits),
no order lifecycle beyond "sent" (no cancels, no partial fills, no execution reports
flowing back), no persistence/audit log, no reconnect/failover logic for the feed or
broker connection, no real FIX or exchange protocol adapter. These are the natural next
slice of work once the pipeline shape is validated — see the roadmap below.

### If you need to go faster than ~1ms

None of this is needed for a 100ms target, but it's what the "everything to learn"
list below is for if you want to push toward HFT-grade latency (single-digit
microseconds) later:

1. Replace the direct method call chain with a **single-producer/single-consumer
   ring buffer** (LMAX Disruptor, or Agrona's `ManyToOneRingBuffer`) so the feed
   thread never blocks on strategy/broker work, and the strategy/broker thread never
   allocates or contends.
2. Pin the hot-path thread to a CPU core (`taskset`/thread affinity) and keep it away
   from GC and OS scheduler noise.
3. Move to a GC that minimizes pause times (ZGC or Shenandoah) or avoid the heap
   entirely for hot objects (Chronicle Bytes / off-heap buffers).
4. Replace TCP/kernel networking with kernel-bypass NICs (Solarflare OpenOnload,
   or io_uring) if the bottleneck is network stack overhead, not application code.
5. Measure with **JMH**, not `System.nanoTime()` in a demo loop — this repo's
   `LatencyRecorder` is fine for "did we blow the 100ms budget," not for microbenchmark-grade
   numbers (JIT warm-up, no dead-code elimination guards, etc. all skew a naive loop).

## Everything to learn

Organized roughly in the order you'd want to learn it, from "needed to understand
this repo" to "needed if you go chase microseconds."

### 1. Java fundamentals this repo leans on
- Records, sealed interfaces, pattern matching (Java 17+)
- Functional interfaces / lambdas (`TickListener` is a `@FunctionalInterface`)
- Immutability and why it matters for concurrent code
- `System.nanoTime()` vs `System.currentTimeMillis()` — monotonic vs wall-clock time

### 2. Testing
- JUnit 5 (Jupiter): `@Test`, lifecycle, parameterized tests
- Mockito: `@Mock`, `when(...).thenReturn(...)`, `verify(...)`, `ArgumentCaptor`
- Test doubles taxonomy: dummy, stub, fake, spy, mock — this repo uses fakes
  (`SimulatedMarketDataFeed`, `SimulatedBrokerGateway`) for integration-style tests and
  mocks for isolated unit tests. Know when to reach for which.
- Property-based testing (e.g. **jqwik**) — useful once strategy math gets more complex
  than a moving average.

### 3. Architecture
- **Ports & adapters / hexagonal architecture** — the core pattern this repo follows.
  Read Alistair Cockburn's original writeup.
- Dependency inversion principle — why `TradingEngine` depends on interfaces it owns,
  not on concrete adapter classes.

### 4. Low-latency / mechanical sympathy (for pushing past 100ms into µs territory)
- **Martin Thompson's "Mechanical Sympathy" blog** — the canonical starting point for
  this whole field.
- **LMAX Disruptor** — the ring-buffer pattern this codebase's synchronous design
  would evolve into for true low-latency work. Read the LMAX whitepaper.
- **Single Writer Principle** and false sharing / `@Contended`
- JIT compilation basics: warm-up, escape analysis, inlining, deoptimization
- GC algorithms: G1 (default), ZGC, Shenandoah — pause-time tradeoffs
- **JMH** (Java Microbenchmark Harness) — the only correct way to microbenchmark JVM code
- Off-heap data structures: **Chronicle Bytes/Queue/Wire**, **Agrona** (`DirectBuffer`,
  ring buffers, off-heap collections)
- **Aeron** — UDP-based low-latency messaging, if you outgrow in-process pub/sub

### 5. Market data & trading protocols
- **FIX protocol** (Financial Information eXchange) — the industry-standard order and
  market data protocol; **QuickFIX/J** is the standard open-source Java implementation
- Exchange-native protocols: Nasdaq ITCH (market data) / OUCH (order entry) as examples
  of binary, low-latency protocols
- Order types (market, limit, stop, IOC, FOK) and order lifecycle (new → ack →
  partial fill → fill / cancel / reject)
- Market data concepts: order book (L1/L2/L3), trade prints, multicast market data
  distribution, sequence numbers and gap detection/recovery

### 6. Production trading system concerns not built here
- Pre-trade risk checks (position limits, notional limits, fat-finger checks)
- Position and P&L tracking
- Reconnect/failover and market data gap recovery
- Backtesting a strategy against historical data before it ever sees a live feed
- Audit logging / event sourcing for compliance and post-trade reconciliation

### 7. Observability for latency-sensitive systems
- **HDRHistogram** — the standard library for accurate latency percentile tracking
  (this repo's `LatencyRecorder` is a toy version of what HDRHistogram does properly)
- Percentiles vs averages — why p99/p999 matter more than mean latency in trading systems
- Clock synchronization (**PTP** — Precision Time Protocol) — needed once you're
  correlating timestamps across machines (exchange, feed handler, strategy host)

### 8. Infra-level (only relevant once you're chasing single-digit microseconds)
- CPU pinning / thread affinity, NUMA-aware memory allocation, huge pages
- Kernel-bypass networking (Solarflare OpenOnload, DPDK, io_uring)
- Co-location and direct market access — why the network hop, not the code, dominates
  latency once the JVM side is tight
