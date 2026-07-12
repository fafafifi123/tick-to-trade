package com.ticktotrade.alpha;

import com.ticktotrade.model.Side;
import com.ticktotrade.model.Signal;
import com.ticktotrade.model.Tick;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MovingAverageCrossoverStrategyTest {

    @Test
    void emitsBuySignalWhenShortAverageCrossesAboveLongAverage() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy("AAPL", 2, 4, 100);

        // Flat prices warm up both averages equal - no signal yet.
        feed(strategy, "AAPL", 100, 100, 100, 100);

        // A sharp move up immediately drags the short average above the long average.
        Signal signal = strategy.evaluate(new Tick("AAPL", 110, 100, System.nanoTime(), System.nanoTime()));

        assertEquals(Side.BUY, signal.side());
        assertEquals(100, signal.quantity());
    }

    @Test
    void ignoresTicksForOtherSymbols() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy("AAPL", 2, 4, 100);

        Signal signal = strategy.evaluate(new Tick("MSFT", 100, 100, 1, 1));

        assertFalse(signal.actionable());
    }

    private static java.util.List<Signal> feed(AlphaStrategy strategy, String symbol, double... prices) {
        java.util.List<Signal> signals = new java.util.ArrayList<>();
        for (double price : prices) {
            signals.add(strategy.evaluate(new Tick(symbol, price, 100, System.nanoTime(), System.nanoTime())));
        }
        return signals;
    }
}
