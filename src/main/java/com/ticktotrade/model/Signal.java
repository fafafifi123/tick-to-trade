package com.ticktotrade.model;

/**
 * The alpha strategy's decision for a given tick. Use {@link #none()} for "no trade" -
 * this avoids nulls at the strategy/engine boundary.
 */
public record Signal(String symbol, Side side, long quantity, boolean actionable) {

    public static Signal none() {
        return new Signal("", null, 0, false);
    }

    public static Signal of(String symbol, Side side, long quantity) {
        return new Signal(symbol, side, quantity, true);
    }
}
