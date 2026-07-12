package com.ticktotrade.feed;

import com.ticktotrade.model.Tick;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulatedMarketDataFeedTest {

    @Test
    void dispatchesTicksOnlyToListenersOfThatSymbol() {
        SimulatedMarketDataFeed feed = new SimulatedMarketDataFeed();
        List<Tick> received = new ArrayList<>();
        feed.subscribe("AAPL", received::add);
        feed.subscribe("MSFT", tick -> {
            throw new AssertionError("should not receive AAPL ticks");
        });
        feed.start();

        Tick tick = new Tick("AAPL", 100.0, 10, 1, 1);
        feed.publish(tick);

        assertEquals(1, received.size());
        assertEquals(tick, received.get(0));
    }

    @Test
    void rejectsPublishBeforeStart() {
        SimulatedMarketDataFeed feed = new SimulatedMarketDataFeed();
        Tick tick = new Tick("AAPL", 100.0, 10, 1, 1);

        assertThrows(IllegalStateException.class, () -> feed.publish(tick));
    }

    @Test
    void ignoresTicksWithNoSubscribers() {
        SimulatedMarketDataFeed feed = new SimulatedMarketDataFeed();
        feed.start();

        feed.publish(new Tick("UNKNOWN", 1.0, 1, 1, 1));
    }
}
