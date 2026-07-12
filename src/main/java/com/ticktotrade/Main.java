package com.ticktotrade;

import com.ticktotrade.alpha.MovingAverageCrossoverStrategy;
import com.ticktotrade.broker.SimulatedBrokerGateway;
import com.ticktotrade.engine.LatencyRecorder;
import com.ticktotrade.engine.TradingEngine;
import com.ticktotrade.feed.SimulatedMarketDataFeed;
import com.ticktotrade.model.Tick;

import java.util.Random;

/**
 * End-to-end wiring demo: simulated feed -> moving average alpha -> simulated
 * broker. Run with {@code mvn -q exec:java} or from your IDE. Swap the
 * simulated feed/broker for real adapters without touching TradingEngine.
 */
public final class Main {

    public static void main(String[] args) {
        String symbol = "AAPL";
        SimulatedMarketDataFeed feed = new SimulatedMarketDataFeed();
        SimulatedBrokerGateway broker = new SimulatedBrokerGateway();
        LatencyRecorder latencyRecorder = new LatencyRecorder(10_000);
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(symbol, 5, 20, 100);
        TradingEngine engine = new TradingEngine(strategy, broker, latencyRecorder);

        feed.subscribe(symbol, engine);
        feed.start();

        Random random = new Random(42);
        double price = 150.0;
        for (int i = 0; i < 5_000; i++) {
            price += (random.nextDouble() - 0.5);
            long now = System.nanoTime();
            feed.publish(new Tick(symbol, price, 100, now, now));
        }
        feed.stop();

        System.out.printf("Ticks processed: 5000%n");
        System.out.printf("Orders sent: %d%n", broker.acceptedOrders().size());
        System.out.printf("Tick-to-order latency: mean=%.0fns p99=%dns max=%dns (n=%d)%n",
                latencyRecorder.meanNanos(),
                latencyRecorder.percentileNanos(99),
                latencyRecorder.maxNanos(),
                latencyRecorder.count());
    }
}
