package com.ticktotrade.alpha;

import com.ticktotrade.model.Signal;
import com.ticktotrade.model.Tick;

/**
 * Turns market data into a trading decision. Implementations may hold internal
 * state (e.g. moving averages) but must be safe to call repeatedly on a single
 * hot-path thread - no I/O, no blocking.
 */
public interface AlphaStrategy {
    Signal evaluate(Tick tick);
}
