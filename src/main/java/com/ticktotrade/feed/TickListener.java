package com.ticktotrade.feed;

import com.ticktotrade.model.Tick;

/**
 * Hot-path callback invoked for every tick. Implementations must not block -
 * the feed thread calls this synchronously for every update.
 */
@FunctionalInterface
public interface TickListener {
    void onTick(Tick tick);
}
