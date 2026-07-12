package com.ticktotrade.alpha;

import com.ticktotrade.model.Side;
import com.ticktotrade.model.Signal;
import com.ticktotrade.model.Tick;

/**
 * Toy alpha: track a short and long simple moving average per-call (single symbol
 * instance) and emit BUY when the short average crosses above the long average,
 * SELL when it crosses below. Fixed-size circular buffers only - no allocation
 * once warmed up, so this is safe to call on the hot path.
 */
public final class MovingAverageCrossoverStrategy implements AlphaStrategy {

    private final String symbol;
    private final long orderQuantity;
    private final double[] shortWindow;
    private final double[] longWindow;
    private int shortIndex;
    private int longIndex;
    private long ticksSeen;
    private double shortSum;
    private double longSum;
    private Double previousShortAvg;
    private Double previousLongAvg;

    public MovingAverageCrossoverStrategy(String symbol, int shortPeriod, int longPeriod, long orderQuantity) {
        if (shortPeriod <= 0 || longPeriod <= 0) {
            throw new IllegalArgumentException("periods must be positive");
        }
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("shortPeriod must be smaller than longPeriod");
        }
        this.symbol = symbol;
        this.orderQuantity = orderQuantity;
        this.shortWindow = new double[shortPeriod];
        this.longWindow = new double[longPeriod];
    }

    @Override
    public Signal evaluate(Tick tick) {
        if (!symbol.equals(tick.symbol())) {
            return Signal.none();
        }

        shortSum -= shortWindow[shortIndex];
        shortWindow[shortIndex] = tick.price();
        shortSum += tick.price();
        shortIndex = (shortIndex + 1) % shortWindow.length;

        longSum -= longWindow[longIndex];
        longWindow[longIndex] = tick.price();
        longSum += tick.price();
        longIndex = (longIndex + 1) % longWindow.length;

        ticksSeen++;
        if (ticksSeen < longWindow.length) {
            return Signal.none();
        }

        double shortAvg = shortSum / shortWindow.length;
        double longAvg = longSum / longWindow.length;
        Signal signal = Signal.none();

        if (previousShortAvg != null && previousLongAvg != null) {
            boolean wasBelow = previousShortAvg <= previousLongAvg;
            boolean nowAbove = shortAvg > longAvg;
            boolean wasAbove = previousShortAvg >= previousLongAvg;
            boolean nowBelow = shortAvg < longAvg;

            if (wasBelow && nowAbove) {
                signal = Signal.of(symbol, Side.BUY, orderQuantity);
            } else if (wasAbove && nowBelow) {
                signal = Signal.of(symbol, Side.SELL, orderQuantity);
            }
        }

        previousShortAvg = shortAvg;
        previousLongAvg = longAvg;
        return signal;
    }
}
