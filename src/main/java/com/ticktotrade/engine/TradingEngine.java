package com.ticktotrade.engine;

import com.ticktotrade.alpha.AlphaStrategy;
import com.ticktotrade.broker.BrokerGateway;
import com.ticktotrade.feed.TickListener;
import com.ticktotrade.model.Order;
import com.ticktotrade.model.Signal;
import com.ticktotrade.model.Tick;

/**
 * Wires a tick to an alpha decision to a broker order, measuring the
 * tick-to-order latency on every call. This is the tick-to-trade hot path:
 * {@link #onTick} must stay allocation-light and must never block on I/O.
 * <p>
 * All three collaborators are interfaces, so unit tests inject mocks or fakes
 * for the strategy and the broker without needing a real market data feed,
 * network socket, or broker connection.
 */
public final class TradingEngine implements TickListener {

    private final AlphaStrategy alphaStrategy;
    private final BrokerGateway brokerGateway;
    private final LatencyRecorder latencyRecorder;

    public TradingEngine(AlphaStrategy alphaStrategy, BrokerGateway brokerGateway, LatencyRecorder latencyRecorder) {
        this.alphaStrategy = alphaStrategy;
        this.brokerGateway = brokerGateway;
        this.latencyRecorder = latencyRecorder;
    }

    @Override
    public void onTick(Tick tick) {
        Signal signal = alphaStrategy.evaluate(tick);

        if (signal.actionable()) {
            long now = System.nanoTime();
            Order order = new Order(signal.symbol(), signal.side(), signal.quantity(), tick.price(), now);
            brokerGateway.send(order);
            latencyRecorder.record(now - tick.arrivalTimestampNanos());
        }
    }

    public LatencyRecorder latencyRecorder() {
        return latencyRecorder;
    }
}
