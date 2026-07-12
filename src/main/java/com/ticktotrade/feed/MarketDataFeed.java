package com.ticktotrade.feed;

/**
 * Source of market data ticks. A real implementation would adapt an exchange
 * multicast feed, a websocket, or a FIX market data session. Tests replace this
 * with an in-memory implementation so no network/exchange dependency is needed.
 */
public interface MarketDataFeed {

    void subscribe(String symbol, TickListener listener);

    void start();

    void stop();
}
