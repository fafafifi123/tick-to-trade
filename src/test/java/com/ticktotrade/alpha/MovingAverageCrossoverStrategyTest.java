package com.ticktotrade.alpha;

import com.ticktotrade.model.Side;
import com.ticktotrade.model.Signal;
import com.ticktotrade.model.Tick;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void emitsSellSignalWhenShortAverageCrossesBelowLongAverage() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy("AAPL", 2, 4, 100);

        // Warm up flat, then cross up (BUY), then cross back down (SELL).
        feed(strategy, "AAPL", 100, 100, 100, 100, 110);
        Signal signal = strategy.evaluate(new Tick("AAPL", 80, 100, System.nanoTime(), System.nanoTime()));

        assertEquals(Side.SELL, signal.side());
        assertEquals(100, signal.quantity());
    }

    @Test
    void ignoresTicksForOtherSymbols() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy("AAPL", 2, 4, 100);

        Signal signal = strategy.evaluate(new Tick("MSFT", 100, 100, 1, 1));

        assertFalse(signal.actionable());
    }

    @Test
    void rejectsNonPositiveShortPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new MovingAverageCrossoverStrategy("AAPL", 0, 4, 100));
    }

    @Test
    void rejectsNonPositiveLongPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new MovingAverageCrossoverStrategy("AAPL", 2, 0, 100));
    }

    @Test
    void rejectsShortPeriodNotSmallerThanLongPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new MovingAverageCrossoverStrategy("AAPL", 4, 4, 100));
    }

    private static java.util.List<Signal> feed(AlphaStrategy strategy, String symbol, double... prices) {
        java.util.List<Signal> signals = new java.util.ArrayList<>();
        for (double price : prices) {
            signals.add(strategy.evaluate(new Tick(symbol, price, 100, System.nanoTime(), System.nanoTime())));
        }
        return signals;
    }
}
