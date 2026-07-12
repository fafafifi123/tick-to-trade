package com.ticktotrade.model;

/**
 * A single market data update for one instrument.
 * exchangeTimestampNanos is the time the exchange/feed says the event happened;
 * arrivalTimestampNanos is when this process observed it. Both are needed to
 * separate network/feed latency from processing latency.
 */
public record Tick(
        String symbol,
        double price,
        long size,
        long exchangeTimestampNanos,
        long arrivalTimestampNanos
) {
}
