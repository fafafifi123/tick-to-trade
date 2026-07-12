package com.ticktotrade.feed;

import com.ticktotrade.model.Tick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory feed for tests and local demos: no sockets, no exchange dependency.
 * Ticks are pushed in with {@link #publish} and dispatched synchronously to
 * every listener subscribed to that symbol, on the caller's thread.
 */
public final class SimulatedMarketDataFeed implements MarketDataFeed {

    private final Map<String, List<TickListener>> listenersBySymbol = new HashMap<>();
    private volatile boolean running;

    @Override
    public void subscribe(String symbol, TickListener listener) {
        listenersBySymbol.computeIfAbsent(symbol, s -> new ArrayList<>()).add(listener);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    public void publish(Tick tick) {
        if (!running) {
            throw new IllegalStateException("feed is not started");
        }
        List<TickListener> listeners = listenersBySymbol.get(tick.symbol());
        if (listeners == null) {
            return;
        }
        for (TickListener listener : listeners) {
            listener.onTick(tick);
        }
    }
}
