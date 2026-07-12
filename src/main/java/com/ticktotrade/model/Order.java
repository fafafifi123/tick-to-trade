package com.ticktotrade.model;

public record Order(
        String symbol,
        Side side,
        long quantity,
        double limitPrice,
        long createdTimestampNanos
) {
}
